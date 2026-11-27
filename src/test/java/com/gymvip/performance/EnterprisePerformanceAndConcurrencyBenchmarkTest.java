package com.gymvip.performance;

import com.gymvip.entity.Account;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.TotpService;
import com.gymvip.service.MemberWorkspaceService;
import com.gymvip.training.ClassReservationsService;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.entity.Account;
import org.springframework.jdbc.core.RowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EMP-17: Benchmark controlado de latencia (p50/p95/p99), rendimiento (ops/s)
 * y contención concurrente de cupos con bloqueo serializado.
 */
class EnterprisePerformanceAndConcurrencyBenchmarkTest {

    @Test
    void jwtAndTotpThroughputMeetsSlaUnderSequentialLoad() {
        JwtService jwtService = new JwtService();
        TotpService totpService = new TotpService();
        String secret = totpService.generateSecret();
        long currentStep = Instant.now().getEpochSecond() / 30L;
        List<Long> latenciesNs = new ArrayList<>(500);

        Account sample = new Account();
        sample.setId(UUID.randomUUID());
        sample.setEmail("warmup@vipcenterfit.pe");
        sample.setRoles(Set.of("ADMIN"));

        // Warmup JVM
        for (int i = 0; i < 50; i++) {
            String tok = jwtService.generateToken(sample, UUID.randomUUID().toString(), LocalDateTime.now().plusHours(4));
            assertTrue(jwtService.validateAndExtractClaims(tok).isPresent());
        }

        long startWallNs = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            long t0 = System.nanoTime();
            Account acc = new Account();
            acc.setId(UUID.randomUUID());
            acc.setEmail("user" + i + "@vipcenterfit.pe");
            acc.setRoles(Set.of("CLIENTE"));

            String token = jwtService.generateToken(acc, UUID.randomUUID().toString(), LocalDateTime.now().plusHours(24));
            var parsed = jwtService.validateAndExtractClaims(token);
            String chal = jwtService.generateMfaChallengeToken(acc);
            var chalParsed = jwtService.validateMfaChallengeToken(chal);
            String code = totpService.generateCodeForStep(secret, currentStep);
            assertTrue(parsed.isPresent());
            assertTrue(chalParsed.isPresent());
            assertTrue(totpService.verifyCode(secret, code));
            latenciesNs.add(System.nanoTime() - t0);
        }
        long elapsedMs = Math.max(1L, (System.nanoTime() - startWallNs) / 1_000_000L);

        Collections.sort(latenciesNs);
        double p50Ms = latenciesNs.get((int) (latenciesNs.size() * 0.50)) / 1_000_000.0;
        double p95Ms = latenciesNs.get((int) (latenciesNs.size() * 0.95)) / 1_000_000.0;
        double p99Ms = latenciesNs.get((int) (latenciesNs.size() * 0.99)) / 1_000_000.0;
        double opsPerSec = 500.0 * 1000.0 / elapsedMs;

        System.out.printf(
                "[PERF BENCHMARK] 500x(JWT+MFA_Challenge+TOTP): elapsed=%dms, throughput=%.1f ops/s, p50=%.3fms, p95=%.3fms, p99=%.3fms%n",
                elapsedMs, opsPerSec, p50Ms, p95Ms, p99Ms
        );
        assertTrue(p95Ms < 50.0, "p95 esperado < 50ms en benchmark local");
    }

    @Test
    void classReservationHandles25ConcurrentRequestsWithoutOverselling() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantScopeResolver scope = mock(TenantScopeResolver.class);
        MemberWorkspaceService workspace = mock(MemberWorkspaceService.class);
        MembershipRepository membershipRepo = mock(MembershipRepository.class);
        UUID branchId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        AtomicInteger confirmedCount = new AtomicInteger(0);
        Object dbRowLock = new Object();

        when(scope.resolveActorByEmail(anyString())).thenReturn(new Account());
        when(scope.memberBranch(any(Account.class), isNull())).thenReturn(branchId);
        when(workspace.ownClient(anyString())).thenAnswer(inv -> UUID.randomUUID());
        when(membershipRepo.contract(any(UUID.class))).thenReturn(
                Optional.of(new MembershipRepository.Contract(UUID.randomUUID(), LocalDate.now().plusDays(15), "VIGENTE"))
        );
        when(jdbc.query(contains("FROM gym_classes c JOIN branches"), any(RowMapper.class), eq(classId)))
                .thenAnswer(inv -> {
                    var row = mock(java.sql.ResultSet.class);
                    when(row.getObject(1, UUID.class)).thenReturn(classId);
                    when(row.getObject(2, UUID.class)).thenReturn(organizationId);
                    when(row.getObject(3, UUID.class)).thenReturn(branchId);
                    when(row.getString(5)).thenReturn("Clase de prueba");
                    when(row.getObject(6, java.time.LocalDateTime.class)).thenReturn(java.time.LocalDateTime.now().plusDays(1));
                    when(row.getInt(7)).thenReturn(5);
                    when(row.getString(8)).thenReturn("PROGRAMADA");
                    return List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(row, 0));
                });
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM class_reservations"), eq(Integer.class), eq(classId)))
                .thenAnswer(inv -> confirmedCount.get());
        when(jdbc.update(contains("INSERT INTO class_reservations"), any(), eq(classId), any(), eq("RESERVADA"), any()))
                .thenAnswer(inv -> {
                    if (confirmedCount.get() >= 5) throw new IllegalStateException("Sobrecupo detectado");
                    confirmedCount.incrementAndGet();
                    return 1;
                });
        ClassReservationsService repo = new ClassReservationsService(jdbc, scope, workspace, membershipRepo, java.time.Clock.systemUTC()) {
            @Override
            public Map<String, Object> reserve(String email, UUID cid) {
                synchronized (dbRowLock) {
                    return super.reserve(email, cid);
                }
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(25);
        CountDownLatch ready = new CountDownLatch(25);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            String memberEmail = "socio" + i + "@vipcenterfit.pe";
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return String.valueOf(repo.reserve(memberEmail, classId).get("status"));
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        long t0 = System.nanoTime();
        start.countDown();

        int reserved = 0;
        int waitlisted = 0;
        for (Future<String> f : futures) {
            String st = f.get(5, TimeUnit.SECONDS);
            if ("RESERVADA".equals(st)) reserved++;
            else if ("LISTA_ESPERA".equals(st)) waitlisted++;
        }
        pool.shutdown();

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf(
                "[CONCURRENCY BENCHMARK] 25 concurrent reservations (capacity=5): reserved=%d, waitlist=%d, elapsed=%dms%n",
                reserved, waitlisted, elapsedMs
        );
        assertEquals(5, reserved);
        assertEquals(20, waitlisted);
    }
}
