package com.gymvip.service;

import com.gymvip.dto.request.ReminderConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceUnitTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private CommsServiceClient commsClient;
    @Mock private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneId.of("America/Lima"));
    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        reminderService = new ReminderService(jdbc, commsClient, auditService, clock);
    }

    private Map<String, Object> mockConfigRow(boolean enabled, String anticipation, int postExpiry, String channels) {
        return Map.of(
                "id", UUID.randomUUID(),
                "enabled", enabled,
                "anticipation_days", anticipation,
                "post_expiry_days", postExpiry,
                "channels", channels,
                "updated_by", "admin@vipcenterfit.pe",
                "updated_at", Timestamp.valueOf("2026-11-01 10:00:00")
        );
    }

    @Test
    @DisplayName("evaluateReminders debe omitir ejecución si recordatorios están desactivados (HU-14 T06)")
    void testEvaluationSkipsWhenDisabled() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
                .thenReturn(mockConfigRow(false, "3,5,7,15", 5, "WHATSAPP,APP"));

        var summary = reminderService.evaluateReminders("admin@vipcenterfit.pe");

        assertNotNull(summary);
        assertEquals(0, summary.candidatesEvaluated());
        assertEquals(0, summary.noticesRequested());
        assertTrue(summary.message().contains("desactivados"));
        verify(commsClient, never()).sendWhatsAppReminder(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("evaluateReminders detecta anticipación (7 días) y solicita despacho WhatsApp (HU-14 T06 / T08)")
    void testEvaluationDetectsAnticipationAndDispatches() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
                .thenReturn(mockConfigRow(true, "3,5,7,15", 5, "WHATSAPP"));

        UUID periodId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.parse("2026-11-11"); // Hoy es 2026-11-03 -> diff = 7 días

        Map<String, Object> candidate = new HashMap<>();
        candidate.put("period_id", periodId);
        candidate.put("client_id", clientId);
        candidate.put("first_name", "Valeria");
        candidate.put("last_name", "Mendoza");
        candidate.put("phone", "+51987654321");
        candidate.put("consent_whatsapp", true);
        candidate.put("consent_app", true);
        candidate.put("plan_name", "Plan VIP Mensual");
        candidate.put("end_date", java.sql.Date.valueOf(expiryDate));

        when(jdbc.queryForList(contains("FROM membership_periods p"))).thenReturn(List.of(candidate));
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"), any(Object[].class))).thenReturn(1);

        when(commsClient.sendWhatsAppReminder(anyString(), anyString(), eq("recordatorio_vencimiento"), anyList(), eq(true), eq(clientId), anyString(), eq(periodId)))
                .thenReturn(new CommsServiceClient.CommsDispatchResult(true, "wamid.sim_12345", "ENVIADO", null));

        var summary = reminderService.evaluateReminders("admin@vipcenterfit.pe");

        assertEquals(1, summary.candidatesEvaluated());
        assertEquals(1, summary.noticesRequested());
        assertEquals(0, summary.noticesSkipped());
        verify(commsClient, times(1)).sendWhatsAppReminder(anyString(), anyString(), eq("recordatorio_vencimiento"), anyList(), eq(true), any(), any(), any());
    }

    @Test
    @DisplayName("evaluateReminders debe respetar clave de idempotencia y omitir avisos repetidos hoy (HU-14 T07)")
    void testEvaluationIdempotencySkipsDuplicate() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
                .thenReturn(mockConfigRow(true, "3,5,7,15", 5, "WHATSAPP"));

        UUID periodId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.parse("2026-11-11");

        Map<String, Object> candidate = new HashMap<>();
        candidate.put("period_id", periodId);
        candidate.put("client_id", clientId);
        candidate.put("first_name", "Valeria");
        candidate.put("last_name", "Mendoza");
        candidate.put("phone", "+51987654321");
        candidate.put("consent_whatsapp", true);
        candidate.put("plan_name", "Plan VIP Mensual");
        candidate.put("end_date", java.sql.Date.valueOf(expiryDate));

        when(jdbc.queryForList(contains("FROM membership_periods p"))).thenReturn(List.of(candidate));
        // Ya despachado hoy -> retorna 1
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"), any(Object[].class))).thenReturn(0);

        var summary = reminderService.evaluateReminders("admin@vipcenterfit.pe");

        assertEquals(1, summary.candidatesEvaluated());
        assertEquals(0, summary.noticesRequested());
        assertEquals(1, summary.noticesSkipped());
        verify(commsClient, never()).sendWhatsAppReminder(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("evaluateReminders omite envío si el socio no cuenta con consentimiento de WhatsApp (HU-14 T10)")
    void testEvaluationSkipsWithoutConsent() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
                .thenReturn(mockConfigRow(true, "3,5,7,15", 5, "WHATSAPP"));

        UUID periodId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.parse("2026-11-11");

        Map<String, Object> candidate = new HashMap<>();
        candidate.put("period_id", periodId);
        candidate.put("client_id", clientId);
        candidate.put("first_name", "Valeria");
        candidate.put("last_name", "Mendoza");
        candidate.put("phone", "+51987654321");
        candidate.put("consent_whatsapp", false); // Sin consentimiento
        candidate.put("plan_name", "Plan VIP Mensual");
        candidate.put("end_date", java.sql.Date.valueOf(expiryDate));

        when(jdbc.queryForList(contains("FROM membership_periods p"))).thenReturn(List.of(candidate));
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"), any(Object[].class))).thenReturn(1);

        var summary = reminderService.evaluateReminders("admin@vipcenterfit.pe");

        assertEquals(1, summary.candidatesEvaluated());
        assertEquals(0, summary.noticesRequested());
        assertEquals(1, summary.noticesSkipped());
        verify(commsClient, never()).sendWhatsAppReminder(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("updateConfig actualiza valores y registra evento de auditoría (HU-14 T04 / T05)")
    void testUpdateConfigUpdatesAndAudits() {
        when(jdbc.update(contains("UPDATE reminder_configurations"), anyBoolean(), anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
                .thenReturn(mockConfigRow(true, "3,7,15", 3, "WHATSAPP"));

        var req = new ReminderConfigRequest(true, List.of(3, 7, 15), 3, List.of("WHATSAPP"));
        var res = reminderService.updateConfig(req, "admin@vipcenterfit.pe");

        assertNotNull(res);
        assertTrue(res.enabled());
        assertEquals(List.of(3, 7, 15), res.anticipationDays());
        assertEquals(3, res.postExpiryDays());
        verify(auditService, times(1)).recordEvent(eq("admin@vipcenterfit.pe"), eq("UPDATE_REMINDER_CONFIG"), eq("ReminderConfig"), eq("GLOBAL"), anyString());
    }

    private Map<String,Object> candidate(String endDate, boolean whatsappConsent) {
        var row=new HashMap<String,Object>();
        row.put("period_id",UUID.randomUUID()); row.put("client_id",UUID.randomUUID());
        row.put("first_name","Sofia"); row.put("last_name","Demo");
        row.put("phone","+51900000001"); row.put("email","socia@example.test");
        row.put("plan_name","Mensual"); row.put("end_date",java.sql.Date.valueOf(endDate));
        row.put("consent_app",true); row.put("consent_whatsapp",whatsappConsent);
        return row;
    }

    @Test void appPersistsBeforeProviderFailureAndDoesNotDependOnWhatsappConsent() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
            .thenReturn(mockConfigRow(true,"3",5,"WHATSAPP,APP,EMAIL"));
        when(jdbc.queryForList(contains("FROM membership_periods p")))
            .thenReturn(List.of(candidate("2026-11-07",true)));
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"),any(Object[].class))).thenReturn(1);
        when(commsClient.sendWhatsAppReminder(any(),any(),any(),any(),anyBoolean(),any(),any(),any()))
            .thenReturn(new CommsServiceClient.CommsDispatchResult(false,null,"FALLIDO","offline"));
        when(commsClient.sendReminderEmail(any(),any(),any(),any()))
            .thenReturn(new CommsServiceClient.CommsDispatchResult(true,"sim_mail_1","SIMULADO",null));
        assertEquals(3,reminderService.evaluateReminders("admin").noticesRequested());
        var order=inOrder(jdbc,commsClient);
        order.verify(jdbc).update(contains("ON CONFLICT(idempotency_key)"),any(),any(),any(),any(),eq("APP"),any(),any(),eq("DISPONIBLE"),any(),any(),any(),isNull(),any(),any());
        order.verify(commsClient).sendWhatsAppReminder(any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }

    @Test void noWhatsappConsentStillCreatesAppNotice() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
            .thenReturn(mockConfigRow(true,"3",5,"WHATSAPP,APP"));
        when(jdbc.queryForList(contains("FROM membership_periods p")))
            .thenReturn(List.of(candidate("2026-11-07",false)));
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"),any(Object[].class))).thenReturn(1);
        var result=reminderService.evaluateReminders("admin");
        assertEquals(1,result.noticesRequested()); assertEquals(1,result.noticesSkipped());
        verifyNoInteractions(commsClient);
    }

    @Test void selectedPostExpiryDaysDoNotGenerateIntermediateDay() {
        var row=new HashMap<>(mockConfigRow(true,"3",5,"APP"));
        row.put("post_expiry_days_selected","1,5");
        when(jdbc.queryForMap(contains("FROM reminder_configurations"))).thenReturn(row);
        when(jdbc.queryForList(contains("FROM membership_periods p")))
            .thenReturn(List.of(candidate("2026-11-02",true))); // Last included day Nov 1: overdue 2 days.
        assertEquals(0,reminderService.evaluateReminders("admin").noticesRequested());
        verify(jdbc,never()).update(contains("ON CONFLICT(idempotency_key)"),any(Object[].class));
    }

    @Test void firstExpiredDayIsEvaluatedUsingExclusiveCoverageBoundary() {
        when(jdbc.queryForMap(contains("FROM reminder_configurations")))
            .thenReturn(mockConfigRow(true,"3",1,"APP"));
        when(jdbc.queryForList(contains("FROM membership_periods p")))
            .thenReturn(List.of(candidate("2026-11-03",true)));
        when(jdbc.update(contains("ON CONFLICT(idempotency_key)"),any(Object[].class))).thenReturn(1);
        assertEquals(1,reminderService.evaluateReminders("admin").noticesRequested());
        verify(jdbc).update(contains("ON CONFLICT(idempotency_key)"),any(),any(),any(),any(),eq("APP"),eq("POST_EXPIRY_1"),any(),any(),any(),any(),any(),any(),any(),any());
    }
}
