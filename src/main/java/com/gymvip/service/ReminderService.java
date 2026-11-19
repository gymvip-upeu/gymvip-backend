package com.gymvip.service;

import com.gymvip.dto.request.ReminderConfigRequest;
import com.gymvip.dto.response.ReminderConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Servicio de evaluación y configuración de recordatorios automáticos de membresía.
 * HU-14 T01, T04, T05, T06, T07, T08.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private static final Set<Integer> ALLOWED_ANTICIPATION_DAYS = Set.of(3, 5, 7, 15);
    private static final Set<String> ALLOWED_CHANNELS = Set.of("WHATSAPP", "EMAIL", "APP");

    private final JdbcTemplate jdbc;
    private final CommsServiceClient commsClient;
    private final AuditService auditService;
    private final Clock clock;

    public ReminderService(
            JdbcTemplate jdbc,
            CommsServiceClient commsClient,
            AuditService auditService,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.commsClient = commsClient;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record EvaluationSummary(
            LocalDate cutDate,
            int candidatesEvaluated,
            int noticesRequested,
            int noticesSkipped,
            String message
    ) {}

    @Transactional(readOnly = true)
    public ReminderConfigResponse getConfig() {
        var row = jdbc.queryForMap("SELECT id, enabled, anticipation_days, post_expiry_days, channels, updated_by, updated_at FROM reminder_configurations LIMIT 1");
        return mapRowToConfig(row);
    }

    @Transactional
    public ReminderConfigResponse updateConfig(ReminderConfigRequest req, String actor) {
        if (req.anticipationDays().stream().anyMatch(day -> day == null || !ALLOWED_ANTICIPATION_DAYS.contains(day))) {
            throw new IllegalArgumentException("Los días de anticipación permitidos son 3, 5, 7 y 15.");
        }
        if (req.channels().stream().anyMatch(channel -> channel == null || !ALLOWED_CHANNELS.contains(channel))) {
            throw new IllegalArgumentException("El canal debe ser WHATSAPP, EMAIL o APP.");
        }

        String anticipationStr = String.join(",", req.anticipationDays().stream().map(String::valueOf).toList());
        String channelsStr = String.join(",", req.channels());
        LocalDateTime now = LocalDateTime.now(clock);

        int updated = jdbc.update("""
            UPDATE reminder_configurations
            SET enabled = ?, anticipation_days = ?, post_expiry_days = ?, channels = ?, updated_by = ?, updated_at = ?
            WHERE id = (SELECT id FROM reminder_configurations LIMIT 1)
        """, req.enabled(), anticipationStr, req.postExpiryDays(), channelsStr, actor, now);

        if (updated == 0) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO reminder_configurations (id, enabled, anticipation_days, post_expiry_days, channels, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, req.enabled(), anticipationStr, req.postExpiryDays(), channelsStr, actor, now);
        }

        auditService.recordEvent(actor, "UPDATE_REMINDER_CONFIG", "ReminderConfig", "GLOBAL",
                String.format("Enabled: %b, Anticipation: [%s], PostExpiry: %d, Channels: [%s]",
                        req.enabled(), anticipationStr, req.postExpiryDays(), channelsStr));

        return getConfig();
    }

    @Transactional
    public EvaluationSummary evaluateReminders(String actor) {
        ReminderConfigResponse config = getConfig();
        LocalDate today = LocalDate.now(clock.withZone(LIMA_ZONE));

        if (!config.enabled()) {
            log.info("[EVALUADOR RECORDATORIOS] Los recordatorios se encuentran desactivados por configuración.");
            return new EvaluationSummary(today, 0, 0, 0, "Recordatorios automáticos desactivados en configuración.");
        }

        Set<Integer> anticipationSet = new HashSet<>(config.anticipationDays());
        int postExpiryDays = config.postExpiryDays();

        // 1. Obtener candidatos con membresía pagada, no cancelada y SIN renovaciones posteriores
        // Regla HU-14 CA-06: Se descartan vencimientos de periodos que ya fueron renovados.
        String sql = """
            SELECT p.id AS period_id, m.client_id, c.first_name, c.last_name, c.phone,
                   (c.consent_whatsapp AND coalesce(mc.whatsapp_consent, false)) AS consent_whatsapp,
                   (c.consent_app AND coalesce(mc.terms_accepted AND mc.privacy_acknowledged, false)) AS consent_app,
                   v.name AS plan_name, p.end_date
            FROM membership_periods p
            JOIN memberships m ON m.id = p.membership_id
            JOIN clients c ON c.id = m.client_id
            JOIN plan_versions v ON v.id = p.plan_version_id
            LEFT JOIN member_consents mc ON mc.client_id = c.id
            WHERE p.cancelled_at_utc IS NULL
              AND c.status = 'ACTIVO'
              AND EXISTS (
                  SELECT 1 FROM payment_records r WHERE r.period_id = p.id AND r.currency = 'PEN'
              )
              AND NOT EXISTS (
                  SELECT 1 FROM membership_periods p2
                  WHERE p2.membership_id = p.membership_id
                    AND p2.start_date > p.end_date
                    AND p2.cancelled_at_utc IS NULL
              )
        """;

        List<Map<String, Object>> candidates = jdbc.queryForList(sql);
        int evaluated = candidates.size();
        int requested = 0;
        int skipped = 0;

        for (Map<String, Object> row : candidates) {
            UUID periodId = (UUID) row.get("period_id");
            UUID clientId = (UUID) row.get("client_id");
            String clientName = row.get("first_name") + " " + row.get("last_name");
            String phone = (String) row.get("phone");
            Boolean consentWhatsapp = (Boolean) row.get("consent_whatsapp");
            String planName = (String) row.get("plan_name");
            LocalDate endDate = ((java.sql.Date) row.get("end_date")).toLocalDate();

            long diffDays = ChronoUnit.DAYS.between(today, endDate);

            String ruleType = null;
            String templateName = null;
            List<String> params = null;

            if (diffDays > 0 && anticipationSet.contains((int) diffDays)) {
                // Caso A: Aviso de anticipación (ej. 15, 7, 5, 3 días antes)
                ruleType = "ANTICIPATION_" + diffDays;
                templateName = "recordatorio_vencimiento";
                params = List.of(clientName, planName, endDate.toString(), String.valueOf(diffDays));
            } else if (diffDays < 0) {
                // Caso B: Aviso posterior al vencimiento (primeros 5 días post-vencimiento)
                long daysOverdue = Math.abs(diffDays);
                if (daysOverdue >= 1 && daysOverdue <= postExpiryDays) {
                    ruleType = "POST_EXPIRY_" + daysOverdue;
                    templateName = "aviso_vencido";
                    params = List.of(clientName, planName, String.valueOf(daysOverdue));
                }
            }

            if (ruleType == null) {
                skipped++;
                continue;
            }

            boolean sent = false;
            String key = String.format("REM-%s-%s-%s", periodId, ruleType, today);
            String whatsApp = normalizeWhatsApp(phone);
            boolean whatsAppConfigured = config.channels().contains("WHATSAPP");
            boolean publishInApp = config.channels().contains("APP") ||
                    (whatsAppConfigured && whatsApp == null);
            if (publishInApp && Boolean.TRUE.equals(row.get("consent_app"))) {
                boolean upcoming = diffDays > 0;
                String title = upcoming ? "Próximo vencimiento de membresía" : "Membresía vencida";
                String message = String.format(
                        "%s, tu plan %s de GYM VIP %s el %s (%d %s).",
                        clientName, planName, upcoming ? "vence" : "venció",
                        endDate, Math.abs(diffDays), upcoming ? "días restantes" : "días de vencido");
                sent = jdbc.update("""
                        INSERT INTO sent_reminder_dispatches (
                            id, idempotency_key, client_id, period_id, channel, rule_type, target_date,
                            status, notice_title, notice_message, created_at
                        )
                        VALUES (?, ?, ?, ?, 'APP', ?, ?, 'ENTREGADO', ?, ?, ?)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """, UUID.randomUUID(), key + "-APP", clientId, periodId, ruleType, today,
                        title, message, LocalDateTime.now(clock)) > 0;
            }

            if (whatsAppConfigured && whatsApp != null) {
                String whatsappKey = key + "-WHATSAPP";
                Integer alreadySent = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sent_reminder_dispatches WHERE idempotency_key = ?",
                        Integer.class, whatsappKey);
                if ((alreadySent == null || alreadySent == 0) && Boolean.TRUE.equals(consentWhatsapp)) {
                    var dispatch = commsClient.sendWhatsAppReminder(
                            whatsappKey, whatsApp, templateName, params, true, clientId, clientName, periodId);
                    jdbc.update("""
                            INSERT INTO sent_reminder_dispatches (
                                id, idempotency_key, client_id, period_id, channel, rule_type, target_date,
                                status, external_delivery_id, error_message, created_at
                            )
                            VALUES (?, ?, ?, ?, 'WHATSAPP', ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (idempotency_key) DO NOTHING
                            """, UUID.randomUUID(), whatsappKey, clientId, periodId, ruleType, today,
                            dispatch.status(), dispatch.deliveryId(), dispatch.errorMessage(), LocalDateTime.now(clock));
                    sent |= dispatch.dispatched();
                }
            }
            if (sent) requested++;
            else skipped++;
        }

        // Registrar auditoría del corte ejecutado
        jdbc.update("""
            INSERT INTO evaluated_reminder_cuts (id, cut_date, triggered_by, eval_timestamp_utc, candidates_evaluated, notices_requested, notices_skipped)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, UUID.randomUUID(), today, actor, LocalDateTime.now(clock), evaluated, requested, skipped);

        log.info("[EVALUADOR RECORDATORIOS] Corte completado: {} candidatos evaluados, {} avisos solicitados, {} omitidos.",
                evaluated, requested, skipped);

        return new EvaluationSummary(today, evaluated, requested, skipped,
                String.format("Evaluación completada: %d evaluados, %d solicitados, %d omitidos.", evaluated, requested, skipped));
    }

    private static String normalizeWhatsApp(String phone) {
        if (phone == null) return null;
        String clean = phone.trim().replaceAll("[ ()-]", "");
        if (clean.matches("9[0-9]{8}")) return "+51" + clean;
        return clean.matches("\\+[1-9][0-9]{7,14}") ? clean : null;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCutsHistory() {
        return jdbc.queryForList("""
            SELECT id, cut_date, triggered_by, eval_timestamp_utc, candidates_evaluated, notices_requested, notices_skipped
            FROM evaluated_reminder_cuts
            ORDER BY eval_timestamp_utc DESC
            LIMIT 50
        """);
    }

    private ReminderConfigResponse mapRowToConfig(Map<String, Object> row) {
        UUID id = (UUID) row.get("id");
        boolean enabled = (Boolean) row.get("enabled");
        String anticipationStr = (String) row.get("anticipation_days");
        int postExpiryDays = ((Number) row.get("post_expiry_days")).intValue();
        String channelsStr = (String) row.get("channels");
        String updatedBy = (String) row.get("updated_by");
        Timestamp ts = (Timestamp) row.get("updated_at");

        List<Integer> anticipationList = Arrays.stream(anticipationStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .sorted()
                .toList();

        List<String> channelsList = Arrays.stream(channelsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        return new ReminderConfigResponse(
                id, enabled, anticipationList, postExpiryDays, channelsList,
                updatedBy, ts != null ? ts.toLocalDateTime() : null
        );
    }
}
