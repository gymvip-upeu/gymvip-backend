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
}