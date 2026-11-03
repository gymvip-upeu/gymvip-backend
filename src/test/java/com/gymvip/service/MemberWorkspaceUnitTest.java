package com.gymvip.service;

import com.gymvip.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberWorkspaceUnitTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ClientService clients;
    @Mock private CoverageService coverage;
    @Mock private AuditService audit;
    @Mock private PasswordEncoder passwords;

    private Clock clock;
    private MemberWorkspaceService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("UTC"));
        service = new MemberWorkspaceService(jdbc, clients, coverage, audit, passwords, clock);
    }

    @Test
    @DisplayName("changePassword debe rechazar contraseñas que superen 72 bytes en UTF-8")
    void testChangePasswordRejectsExceeding72Bytes() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("cliente@miembros.gymvip.local")))
                .thenReturn(List.of(clientId));
        when(jdbc.queryForMap(anyString(), eq("cliente@miembros.gymvip.local")))
                .thenReturn(Map.of("id", UUID.randomUUID(), "password_hash", "$2a$10$hash"));
        when(passwords.matches("TempPass123!", "$2a$10$hash")).thenReturn(true);

        String longPassword = "A".repeat(73) + "a1!";
        var req = new MemberWorkspaceService.PasswordChange("TempPass123!", longPassword);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("cliente@miembros.gymvip.local", req));
        assertTrue(ex.getMessage().contains("demasiado larga"));
    }

    @Test
    @DisplayName("changePassword debe rechazar contraseñas que no cumplan requisitos de complejidad o sean iguales a la actual")
    void testChangePasswordRejectsWeakOrIdenticalPassword() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("cliente@miembros.gymvip.local")))
                .thenReturn(List.of(clientId));
        when(jdbc.queryForMap(anyString(), eq("cliente@miembros.gymvip.local")))
                .thenReturn(Map.of("id", UUID.randomUUID(), "password_hash", "$2a$10$hash"));
        when(passwords.matches("TempPass123!", "$2a$10$hash")).thenReturn(true);

        // Caso 1: idéntica
        var reqIdentical = new MemberWorkspaceService.PasswordChange("TempPass123!", "TempPass123!");
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("cliente@miembros.gymvip.local", reqIdentical));

        // Caso 2: sin mayúsculas
        var reqNoUpper = new MemberWorkspaceService.PasswordChange("TempPass123!", "password12345!");
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("cliente@miembros.gymvip.local", reqNoUpper));

        // Caso 3: sin dígitos
        var reqNoDigit = new MemberWorkspaceService.PasswordChange("TempPass123!", "PasswordCompleja!");
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("cliente@miembros.gymvip.local", reqNoDigit));
    }

    @Test
    @DisplayName("changePassword debe revocar sesiones al actualizar exitosamente la contraseña")
    void testChangePasswordRevokesSessionsOnSuccess() {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("cliente@miembros.gymvip.local")))
                .thenReturn(List.of(clientId));
        when(jdbc.queryForMap(anyString(), eq("cliente@miembros.gymvip.local")))
                .thenReturn(Map.of("id", accountId, "password_hash", "$2a$10$hash"));
        when(passwords.matches("TempPass123!", "$2a$10$hash")).thenReturn(true);
        when(passwords.encode("NuevaPassword123!")).thenReturn("$2a$10$newhash");

        var req = new MemberWorkspaceService.PasswordChange("TempPass123!", "NuevaPassword123!");
        service.changePassword("cliente@miembros.gymvip.local", req);

        verify(jdbc, times(1)).update(contains("DELETE FROM sessions WHERE account_id=?"), eq(accountId));
        verify(audit, times(1)).recordEvent(eq("cliente@miembros.gymvip.local"), eq("CHANGE_MEMBER_PASSWORD"), eq("Account"), eq(accountId.toString()), anyString());
    }

    @Test
    @DisplayName("provision debe fallar si el cliente ya cuenta con acceso a la app")
    void testProvisionFailsIfAlreadyHasAccess() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForMap(contains("account_id,member_code,status"), eq(clientId)))
                .thenReturn(Map.of("account_id", UUID.randomUUID(), "member_code", "CLI-001", "status", "ACTIVO"));

        assertThrows(ConflictException.class, () -> service.provision(clientId, "admin@vipcenterfit.pe"));
    }

    @Test
    @DisplayName("checkIn debe registrar asistencia cuando el miembro cuenta con cobertura autorizada (HU-13 T09)")
    void testCheckInSuccessWhenCoverageAllowed() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));

        var coverageResponse = new com.gymvip.dto.response.CoverageResponse(
                clientId, "MAR-123456", "Juan Perez", "DNI 12345678", "ACTIVO", "VIGENTE",
                "VIGENTE", true, "Cobertura pagada confirmada para hoy.", UUID.randomUUID(), "Plan Mensual",
                java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-30"),
                java.time.LocalDate.parse("2026-12-01"), 29L, Instant.now(), "America/Lima", 7
        );
        when(coverage.verify(clientId)).thenReturn(coverageResponse);
        when(jdbc.update(contains("INSERT INTO member_attendance"), any(), eq(clientId), any(), any(), eq("recepcion@vipcenterfit.pe")))
                .thenReturn(1);

        var result = service.checkIn(clientId, "recepcion@vipcenterfit.pe");

        assertNotNull(result);
        assertEquals(true, result.get("recorded"));
        assertEquals("Asistencia registrada.", result.get("message"));
        verify(audit, times(1)).recordEvent(eq("recepcion@vipcenterfit.pe"), eq("MEMBER_CHECK_IN"), eq("Client"), eq(clientId.toString()), anyString());
    }

    @Test
    @DisplayName("checkIn debe ser idempotente y no fallar si el miembro ya registró asistencia hoy (HU-13 T09)")
    void testCheckInDuplicateTodayHandledGracefully() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));

        var coverageResponse = new com.gymvip.dto.response.CoverageResponse(
                clientId, "MAR-123456", "Juan Perez", "DNI 12345678", "ACTIVO", "VIGENTE",
                "VIGENTE", true, "Cobertura pagada confirmada para hoy.", UUID.randomUUID(), "Plan Mensual",
                java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-30"),
                java.time.LocalDate.parse("2026-12-01"), 29L, Instant.now(), "America/Lima", 7
        );
        when(coverage.verify(clientId)).thenReturn(coverageResponse);
        // ON CONFLICT DO NOTHING -> 0 filas insertadas
        when(jdbc.update(contains("INSERT INTO member_attendance"), any(), eq(clientId), any(), any(), eq("recepcion@vipcenterfit.pe")))
                .thenReturn(0);

        var result = service.checkIn(clientId, "recepcion@vipcenterfit.pe");

        assertNotNull(result);
        assertEquals(false, result.get("recorded"));
        assertEquals("La asistencia de hoy ya estaba registrada.", result.get("message"));
        verify(audit, never()).recordEvent(any(), eq("MEMBER_CHECK_IN"), any(), any(), any());
    }

    @Test
    @DisplayName("checkIn debe lanzar ConflictException si el miembro no cuenta con cobertura activa (HU-13 T07)")
    void testCheckInFailsWhenCoverageDenied() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));

        var coverageResponse = new com.gymvip.dto.response.CoverageResponse(
                clientId, "MAR-123456", "Juan Perez", "DNI 12345678", "ACTIVO", "VENCIDO",
                "VENCIDO", false, "Sin cobertura pagada para hoy. Requiere renovación.", UUID.randomUUID(), "Plan Mensual",
                java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"),
                java.time.LocalDate.parse("2026-11-01"), 0L, Instant.now(), "America/Lima", 7
        );
        when(coverage.verify(clientId)).thenReturn(coverageResponse);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.checkIn(clientId, "recepcion@vipcenterfit.pe"));
        assertTrue(ex.getMessage().contains("No se puede registrar asistencia"));
        verify(jdbc, never()).update(contains("INSERT INTO member_attendance"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("attendanceHistory debe retornar historial de asistencias ordenadas por fecha (HU-13 T10)")
    void testAttendanceHistoryReturnsRows() {
        UUID clientId = UUID.randomUUID();
        when(clients.getClientById(clientId)).thenReturn(mock(com.gymvip.dto.response.ClientDetailResponse.class));

        var mockRows = new ArrayList<Map<String, Object>>();
        mockRows.add(new HashMap<>(Map.of("id", UUID.randomUUID(), "attendance_date", "2026-11-01", "checked_at_utc", "2026-11-01T12:00:00", "recorded_by", "recepcion@vipcenterfit.pe")));
        when(jdbc.queryForList(contains("FROM member_attendance WHERE client_id=?"), eq(clientId)))
                .thenReturn(mockRows);

        var history = service.attendanceHistory(clientId);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("2026-11-01", history.get(0).get("attendance_date"));
    }
}