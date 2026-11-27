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

    @Test
    @DisplayName("addFavorite debe agregar producto a favoritos del socio (HU-41)")
    void testAddFavoriteSuccess() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForList(contains("SELECT id FROM member_favorites"), eq(clientId), eq("PROD-001"))).thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO member_favorites"), any(), eq(clientId), eq("PROD-001"), eq("Proteina Whey"), eq("Suplementos"), eq("Tomar post-entreno"), any(), any())).thenReturn(1);
        when(jdbc.query(contains("FROM member_favorites f"), any(RowMapper.class), eq(clientId), any(UUID.class)))
                .thenReturn(List.of(Map.of("productId", "PROD-001")));

        var req = new MemberWorkspaceService.FavoriteRequest("PROD-001", "Proteina Whey", "Suplementos", "Tomar post-entreno");
        var result = service.addFavorite(clientId, req);

        assertNotNull(result);
        assertEquals("PROD-001", result.get("productId"));
    }

    @Test
    @DisplayName("addFavorite devuelve el favorito existente sin insertar otro")
    void testAddFavoriteIsIdempotentWhenDuplicate() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForList(contains("SELECT id FROM member_favorites"), eq(clientId), eq("PROD-001"))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        when(jdbc.query(contains("FROM member_favorites f"), any(RowMapper.class), eq(clientId), any(UUID.class)))
                .thenReturn(List.of(Map.of("productId", "PROD-001")));

        var req = new MemberWorkspaceService.FavoriteRequest("PROD-001", "Proteina Whey", "Suplementos", "Tomar post-entreno");
        assertEquals("PROD-001", service.addFavorite(clientId, req).get("productId"));
        verify(jdbc, never()).update(contains("INSERT INTO member_favorites"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("generateDynamicQr debe generar token de acceso con TTL de 60s (HU-13)")
    void testGenerateDynamicQrSuccess() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForMap(contains("SELECT member_code, first_name, last_name, status FROM clients"), eq(clientId)))
                .thenReturn(Map.of("member_code", "CLI-999", "first_name", "Valeria", "last_name", "Mendoza", "status", "ACTIVO"));
        when(jdbc.update(contains("INSERT INTO member_dynamic_qr_tokens"), any(), eq(clientId), anyString(), any(), any())).thenReturn(1);

        var qr = service.generateDynamicQr(clientId);

        assertNotNull(qr);
        assertEquals("CLI-999", qr.memberCode());
        assertEquals("Valeria Mendoza", qr.memberName());
        assertEquals(60, qr.ttlSeconds());
        assertTrue(qr.token().startsWith("gymvip://access/v1?cid="));
        assertTrue(qr.token().contains("code=CLI-999"));
        assertTrue(qr.disclaimer().contains("No es autorizaci"));
    }

    @Test
    @DisplayName("listFaqs debe retornar las FAQs oficiales del gimnasio (HU-28)")
    void testListFaqs() {
        var faqs = service.listFaqs();
        assertNotNull(faqs);
        assertFalse(faqs.isEmpty());
        assertTrue(faqs.stream().anyMatch(f -> f.question().contains("vigencia")));
    }

    @Test
    @DisplayName("listServices debe retornar los servicios e instalaciones publicadas (HU-33)")
    void testListServices() {
        var services = service.listServices();
        assertNotNull(services);
        assertFalse(services.isEmpty());
        assertTrue(services.stream().anyMatch(s -> s.name().contains("Musculación")));
    }

    @Test
    @DisplayName("getConsents y saveConsents deben persistir y sincronizar consentimiento de WhatsApp (HU-05, HU-15)")
    void testConsentsCycle() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));

        var initial = service.getConsents(clientId);
        assertNotNull(initial);
        assertEquals("v1.0", initial.documentVersion());

        var req = new MemberWorkspaceService.ConsentRequest(true, true, true, "v1.0");
        var saved = service.saveConsents(clientId, req);

        assertTrue(saved.termsAccepted());
        assertTrue(saved.privacyAcknowledged());
        assertTrue(saved.whatsappConsent());
        verify(jdbc, atLeastOnce()).update(contains("UPDATE clients SET consent_whatsapp=?"), eq(true), eq(clientId));
    }

    @Test
    @DisplayName("listInbox y markInboxRead deben gestionar bandeja y lecturas por socio (HU-15, HU-34)")
    void testInboxAndMarkRead() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        lenient().when(jdbc.query(contains("member_inbox_reads"), any(RowMapper.class), eq(clientId))).thenReturn(List.of());
        lenient().when(jdbc.query(contains("sent_reminder_dispatches"), any(RowMapper.class), any())).thenReturn(List.of());
        lenient().when(jdbc.queryForObject(contains("EXISTS"), eq(Boolean.class), eq("item-123"), eq(clientId))).thenReturn(true);

        when(jdbc.query(contains("class_reservation_events"), any(RowMapper.class), eq(clientId))).thenReturn(List.of());
        var inbox = service.listInbox(clientId);
        assertNotNull(inbox);
        assertFalse(inbox.isEmpty());
        assertTrue(inbox.stream().anyMatch(i -> i.title().contains("GYM VIP")));

        var result = service.markInboxRead(clientId, "item-123");
        assertEquals("item-123", result.get("itemId"));
        assertEquals(true, result.get("read"));
    }

    @Test
    @DisplayName("requestAccountDeletion: revoca sesiones, tokens QR y desactiva cuenta con trazabilidad LPDP Ley 29733")
    void testRequestAccountDeletion_Success_FullRevocation() {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForList(eq("SELECT id, status, account_id FROM clients WHERE id = ?"), eq(clientId)))
                .thenReturn(List.of(Map.of("id", clientId, "status", "ACTIVO", "account_id", accountId)));

        var result = service.requestAccountDeletion(clientId, "Solicitud de prueba por el titular");

        assertNotNull(result);
        assertEquals("REGISTRADA", result.status());
        assertTrue(result.trackingCode().startsWith("GYM-DEL-"));
        assertTrue(result.accessRevoked());
        assertTrue(result.sessionsPurged());
        assertEquals("10 días hábiles", result.legalTerm());

        // Verificaciones de revocación explícita
        verify(jdbc).update(contains("UPDATE member_consents"), any(), eq(clientId));
        verify(jdbc).update(contains("UPDATE clients"), any(), eq(clientId));
        verify(jdbc).update(contains("UPDATE accounts"), any(), eq(accountId));
        verify(jdbc).update(eq("DELETE FROM sessions WHERE account_id = ?"), eq(accountId));
        verify(jdbc).update(eq("DELETE FROM member_dynamic_qr_tokens WHERE client_id = ?"), eq(clientId));
        verify(jdbc).update(eq("DELETE FROM member_favorites WHERE client_id = ?"), eq(clientId));
        verify(jdbc).update(contains("INSERT INTO client_change_history"), any(), eq(clientId), eq("ACTIVO"), contains("INACTIVO_EXPEDIENTE_"), any());
    }

    @Test
    @DisplayName("requestAccountDeletion: llamada idempotente en cuenta ya inactiva retorna YA_REGISTRADA con expediente previo")
    void testRequestAccountDeletion_Idempotent_AlreadyInactive() {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(clientId))).thenReturn(List.of(clientId));
        when(jdbc.queryForList(eq("SELECT id, status, account_id FROM clients WHERE id = ?"), eq(clientId)))
                .thenReturn(List.of(Map.of("id", clientId, "status", "INACTIVO", "account_id", accountId)));
        when(jdbc.queryForList(contains("SELECT new_value, changed_at FROM client_change_history"), eq(clientId)))
                .thenReturn(List.of(Map.of("new_value", "INACTIVO_EXPEDIENTE_GYM-DEL-777888", "changed_at", "2026-11-16T12:00:00")));

        var result = service.requestAccountDeletion(clientId, "Reintento de supresión");

        assertNotNull(result);
        assertEquals("YA_REGISTRADA", result.status());
        assertEquals("GYM-DEL-777888", result.trackingCode());
        assertTrue(result.accessRevoked());
        assertTrue(result.sessionsPurged());

        // En caso idempotente no debe re-eliminar ni generar nuevo historial
        verify(jdbc, never()).update(contains("UPDATE accounts"), any(), any());
        verify(jdbc, never()).update(contains("INSERT INTO client_change_history"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("requestAccountDeletion: cliente inexistente o nulo lanza excepción correspondiente")
    void testRequestAccountDeletion_ValidationErrors() {
        assertThrows(IllegalArgumentException.class, () -> service.requestAccountDeletion(null, "Razón"));

        UUID nonExistentId = UUID.randomUUID();
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(nonExistentId))).thenReturn(List.of(nonExistentId));
        when(jdbc.queryForList(eq("SELECT id, status, account_id FROM clients WHERE id = ?"), eq(nonExistentId)))
                .thenReturn(List.of());

        assertThrows(NoSuchElementException.class, () -> service.requestAccountDeletion(nonExistentId, "Razón"));
    }

    @Test
    @DisplayName("registerDeviceToken: registra y actualiza token FCM con plataforma")
    void testRegisterDeviceToken() {
        UUID clientId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("FROM sessions"), eq(Boolean.class), eq(clientId), eq("session-test"))).thenReturn(true);

        var req = new MemberWorkspaceService.DeviceTokenRegistration("fcm_token_sample_12345", "android", "Pixel 8 Pro");
        var result = service.registerDeviceToken(clientId, req, "session-test");

        assertNotNull(result);
        assertEquals(true, result.get("registered"));
        assertEquals("ANDROID", result.get("platform"));
        verify(jdbc).update(contains("INSERT INTO member_device_tokens"), eq(clientId), eq("fcm_token_sample_12345"), eq("ANDROID"), eq("Pixel 8 Pro"), eq("session-test"));

        // Validación de token vacío
        assertThrows(IllegalArgumentException.class, () ->
                service.registerDeviceToken(clientId, new MemberWorkspaceService.DeviceTokenRegistration("  ", "android", null), "session-test"));
    }
}
