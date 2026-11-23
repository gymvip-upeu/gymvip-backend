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
import java.time.OffsetDateTime;
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
        var row = jdbc.queryForMap("SELECT id, enabled, anticipation_days, post_expiry_days, post_expiry_days_selected, channels, updated_by, updated_at FROM reminder_configurations LIMIT 1");
        return mapRowToConfig(row);
    }

    @Transactional
    public ReminderConfigResponse updateConfig(ReminderConfigRequest req, String actor) {
        if (req.channels().isEmpty() || req.channels().stream().anyMatch(c -> !Set.of("APP", "WHATSAPP").contains(c)))
            throw new IllegalArgumentException("Selecciona WhatsApp o app móvil como canal de recordatorios.");
        String anticipationStr = String.join(",", req.anticipationDays().stream().map(String::valueOf).toList());
        String channelsStr = String.join(",", req.channels());
        OffsetDateTime now = OffsetDateTime.now(clock);

        int updated = jdbc.update("""
            UPDATE reminder_configurations
            SET enabled = ?, anticipation_days = ?, post_expiry_days = ?, post_expiry_days_selected = ?, channels = ?, updated_by = ?, updated_at = ?
            WHERE id = (SELECT id FROM reminder_configurations LIMIT 1)
        """, req.enabled(), anticipationStr, req.postExpiryDays(), joinDays(req.postExpiryDaysSelected()), channelsStr, actor, now);

        if (updated == 0) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO reminder_configurations (id, enabled, anticipation_days, post_expiry_days, post_expiry_days_selected, channels, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, req.enabled(), anticipationStr, req.postExpiryDays(), joinDays(req.postExpiryDaysSelected()), channelsStr, actor, now);
        }

        auditService.recordEvent(actor, "UPDATE_REMINDER_CONFIG", "ReminderConfig", "GLOBAL",
                String.format("Enabled: %b, Anticipation: [%s], PostExpiry: %d, Channels: [%s]",
                        req.enabled(), anticipationStr, req.postExpiryDays(), channelsStr));

        return getConfig();
    }

    // Each insert commits before network calls. Provider failures cannot roll back the inbox.
    // ON CONFLICT arbitrates concurrent evaluations: only the insert winner sends.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public EvaluationSummary evaluateReminders(String actor) {
        var config = getConfig();
        var today = LocalDate.now(clock.withZone(LIMA_ZONE));
        if (!config.enabled()) return new EvaluationSummary(today, 0, 0, 0, "Recordatorios desactivados.");
        var candidates = jdbc.queryForList("""
            SELECT p.id AS period_id, m.client_id, c.first_name, c.last_name, c.phone, c.email,
                   c.consent_whatsapp, c.consent_app, v.name AS plan_name, p.end_date
            FROM membership_periods p
            JOIN memberships m ON m.id=p.membership_id
            JOIN clients c ON c.id=m.client_id
            JOIN plan_versions v ON v.id=p.plan_version_id
            WHERE p.cancelled_at_utc IS NULL AND c.status='ACTIVO'
              AND EXISTS (SELECT 1 FROM payment_records r WHERE r.period_id=p.id AND r.currency='PEN')
              AND NOT EXISTS (
                SELECT 1 FROM membership_periods p2 WHERE p2.membership_id=p.membership_id
                  AND p2.start_date >= p.end_date AND p2.cancelled_at_utc IS NULL
                  AND EXISTS (SELECT 1 FROM payment_records r2 WHERE r2.period_id=p2.id AND r2.currency='PEN')
              )
            """);
        int requested=0, skipped=0;
        for (var row : candidates) {
            UUID periodId=(UUID)row.get("period_id"), clientId=(UUID)row.get("client_id");
            String name=row.get("first_name")+" "+row.get("last_name"), plan=(String)row.get("plan_name");
            // Coverage is [start,end). Display its last included day, as on the membership screen.
            LocalDate lastDay=((java.sql.Date)row.get("end_date")).toLocalDate().minusDays(1);
            long days=ChronoUnit.DAYS.between(today,lastDay);
            boolean before=days>0 && config.anticipationDays().contains((int)days);
            boolean after=days<0 && config.postExpiryDaysSelected().contains((int)-days);
            if (!before && !after) { skipped++; continue; }
            String rule=(before ? "ANTICIPATION_" : "POST_EXPIRY_")+Math.abs(days);
            String template=before ? "recordatorio_vencimiento" : "aviso_vencido";
            String title=before ? "Tu membresía está por vencer" : "Tu membresía ha vencido";
            String date=lastDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String message=String.format("Hola %s, tu plan %s en VIP CENTER FIT %s el %s (%s %d días). Puedes renovar en recepción.",
                name,plan,before ? "vence" : "venció",date,before ? "en" : "hace",Math.abs(days));
            List<String> params=List.of(name,plan,date,String.valueOf(Math.abs(days)));
            List<Map<String,Object>> external=new ArrayList<>();
            // Persist APP and all channel attempts before invoking any provider.
            for (String channel : config.channels().stream().sorted(Comparator.comparingInt(c -> c.equals("APP") ? 0 : 1)).toList()) {
                boolean allowed=!channel.equals("WHATSAPP") || Boolean.TRUE.equals(row.get("consent_whatsapp"));
                allowed &= !channel.equals("APP") || Boolean.TRUE.equals(row.get("consent_app"));
                String target=String.valueOf(row.get("phone"));
                String key=String.format("REM-%s-%s-%s-%s",periodId,rule,today,channel);
                UUID id=UUID.randomUUID();
                String status=!allowed ? "OMITIDO" : channel.equals("APP") ? "DISPONIBLE" : "SOLICITADO";
                int inserted=jdbc.update("""
                    INSERT INTO sent_reminder_dispatches
                    (id,idempotency_key,client_id,period_id,channel,rule_type,target_date,status,title,message,template_name,error_message,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(idempotency_key) DO NOTHING
                    """,id,key,clientId,periodId,channel,rule,today,status,title,message,template,
                    allowed ? null : "Canal sin consentimiento o destinatario válido",OffsetDateTime.now(clock),OffsetDateTime.now(clock));
                if (inserted==0 || !allowed) { skipped++; continue; }
                requested++;
                if (channel.equals("APP")) {
                    jdbc.update("""
                        INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status,inbox_item_id)
                        VALUES (?,?,'PUSH',?,?,?,'PENDING',?) ON CONFLICT(idempotency_key) DO NOTHING
                        """,UUID.randomUUID(),"reminder_push_"+id,clientId.toString(),title,message,id.toString());
                }
                if (!channel.equals("APP")) external.add(Map.of("id",id,"key",key,"channel",channel,"target",target));
            }
            for (var attempt : external) {
                var result=commsClient.sendWhatsAppReminder((String)attempt.get("key"),(String)attempt.get("target"),template,params,true,clientId,name,periodId);
                jdbc.update("UPDATE sent_reminder_dispatches SET status=?,external_delivery_id=?,error_message=?,updated_at=? WHERE id=?",
                    result.status(),result.deliveryId(),result.errorMessage(),OffsetDateTime.now(clock),attempt.get("id"));
            }
        }
        jdbc.update("""
            INSERT INTO evaluated_reminder_cuts (id,cut_date,triggered_by,eval_timestamp_utc,candidates_evaluated,notices_requested,notices_skipped)
            VALUES (?,?,?,?,?,?,?)
            """,UUID.randomUUID(),today,actor,OffsetDateTime.now(clock),candidates.size(),requested,skipped);
        return new EvaluationSummary(today,candidates.size(),requested,skipped,
            String.format("%d periodos evaluados; %d avisos por canal registrados; %d omitidos o repetidos.",candidates.size(),requested,skipped));
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> getCommunicationHistory() {
        return jdbc.queryForList("""
            SELECT d.id, d.client_id AS "clientId", d.period_id AS "periodId", d.channel,
                   c.first_name || ' ' || c.last_name AS "clientName", d.title, d.message,
                   d.rule_type AS "ruleType", d.template_name AS "templateName",
                   CASE WHEN d.channel='APP' AND r.item_id IS NOT NULL THEN 'LEIDO' ELSE d.status END AS status,
                   d.error_message AS "errorMessage", d.created_at AS "createdAt", r.read_at_utc AS "readAt"
            FROM sent_reminder_dispatches d JOIN clients c ON c.id=d.client_id
            LEFT JOIN member_inbox_reads r ON r.client_id=d.client_id AND r.item_id=d.id::text AND d.channel='APP'
            WHERE d.channel IN ('APP','WHATSAPP')
            ORDER BY d.created_at DESC LIMIT 500
            """);
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

    private static String joinDays(List<Integer> days) {
        return String.join(",",days.stream().map(String::valueOf).toList());
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
                .filter(s -> Set.of("APP", "WHATSAPP").contains(s))
                .distinct()
                .toList();

        return new ReminderConfigResponse(
                id, enabled, anticipationList, postExpiryDays, channelsList,
                Arrays.stream(String.valueOf(row.getOrDefault("post_expiry_days_selected", joinDays(java.util.stream.IntStream.rangeClosed(1,postExpiryDays).boxed().toList()))).split(",")).map(Integer::parseInt).toList(),
                updatedBy, ts != null ? ts.toLocalDateTime() : null
        );
    }
}
