package com.gymvip.performance;

import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.TotpService;
import com.gymvip.service.MemberWorkspaceService;
import com.gymvip.training.TrainingRepository;
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
        AccountRepository accountRepo = mock(AccountRepository.class);
        MemberWorkspaceService workspace = mock(MemberWorkspaceService.class);
        MembershipRepository membershipRepo = mock(MembershipRepository.class);

        UUID classId = UUID.randomUUID();
        AtomicInteger confirmedCount = new AtomicInteger(0);
        Object dbRowLock = new Object();

        when(workspace.ownClient(anyString())).thenAnswer(inv -> UUID.randomUUID());
        when(membershipRepo.contract(any(UUID.class))).thenReturn(
                Optional.of(new MembershipRepository.Contract(UUID.randomUUID(), LocalDate.now().plusDays(15), "VIGENTE"))
        );
        when(jdbc.queryForList(contains("FROM class_reservations"), eq(classId), any(UUID.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("FROM gym_classes WHERE id = ? FOR UPDATE"), eq(Integer.class), eq(classId)))
                .thenReturn(5);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM class_reservations"), eq(Integer.class), eq(classId)))
                .thenAnswer(inv -> {
                    synchronized (dbRowLock) {
                        return confirmedCount.get();
                    }
                });
        when(jdbc.update(contains("INSERT INTO class_reservations"), any(), eq(classId), any(), eq("RESERVADA")))
                .thenAnswer(inv -> {
                    synchronized (dbRowLock) {
                        if (confirmedCount.get() >= 5) {
                            throw new IllegalStateException("Oversell detectado");
                        }
                        confirmedCount.incrementAndGet();
                        return 1;
                    }
                });
        when(jdbc.update(contains("INSERT INTO class_reservations"), any(), eq(classId), any(), eq("LISTA_ESPERA")))
                .thenReturn(1);

        // Serializa llamadas simulando el bloqueo de fila SELECT FOR UPDATE de PostgreSQL
        TrainingRepository repo = new TrainingRepository(jdbc, accountRepo, workspace, membershipRepo) {
            @Override
            public Map<String, Object> reserveClass(String email, UUID cid) {
                synchronized (dbRowLock) {
                    return super.reserveClass(email, cid);
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
                return String.valueOf(repo.reserveClass(memberEmail, classId).get("status"));
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
