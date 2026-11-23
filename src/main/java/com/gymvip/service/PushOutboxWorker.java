package com.gymvip.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PushOutboxWorker {
    private final JdbcTemplate jdbc;
    private final FirebaseMessaging messaging;
    private final boolean enabled;

    public PushOutboxWorker(JdbcTemplate jdbc, ObjectProvider<FirebaseMessaging> messaging,
                            @Value("${comms.push.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.messaging = messaging.getIfAvailable();
        this.enabled = enabled;
    }

    record Pending(UUID id, String key, String recipient, String title, String payload, int retries) {}

    @Scheduled(fixedDelayString = "${PUSH_OUTBOX_POLL_MS:15000}")
    @Transactional
    public void process() {
        if (!enabled || messaging == null) return;
        List<Pending> jobs = jdbc.query("""
            SELECT id, idempotency_key, recipient, title, payload, retry_count
            FROM notification_outbox
            WHERE channel='PUSH' AND status='PENDING'
            ORDER BY created_at_utc LIMIT 20 FOR UPDATE SKIP LOCKED
        """, (r, n) -> new Pending(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
                r.getString(4), r.getString(5), r.getInt(6)));
        for (Pending job : jobs) {
            if (job.recipient().equals("ALL_MEMBERS") || job.recipient().equals("ALL_ACTIVE_MEMBERS") ||
                    job.recipient().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                List<String> tokens = targetTokens(job.recipient());
                for (String token : tokens) {
                    jdbc.update("""
                        INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status)
                        VALUES (?,?, 'PUSH', ?, ?, ?, 'PENDING') ON CONFLICT(idempotency_key) DO NOTHING
                    """, UUID.randomUUID(), "push_" + digest(job.key() + ":" + token), token, job.title(), job.payload());
                }
                markSent(job.id());
                continue;
            }
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM member_device_tokens d JOIN clients c ON c.id=d.client_id
                    WHERE d.device_token=? AND d.is_active=true AND c.status='ACTIVO' AND c.consent_app=true)
                    """,Boolean.class,job.recipient()))) {
                jdbc.update("UPDATE notification_outbox SET status='SKIPPED',error_message='Sin dispositivo o autorización vigente',processed_at_utc=CURRENT_TIMESTAMP WHERE id=?",job.id());
                continue;
            }
            try {
                messaging.send(Message.builder().setToken(job.recipient())
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(job.title() == null ? "GYM VIP" : job.title())
                                .setBody(job.payload()).build()).build());
                markSent(job.id());
            } catch (Exception ex) {
                int retries = job.retries() + 1;
                jdbc.update("""
                    UPDATE notification_outbox SET retry_count=?, status=?, error_message=?, processed_at_utc=CASE WHEN ?>=3 THEN CURRENT_TIMESTAMP ELSE NULL END
                    WHERE id=?
                """, retries, retries >= 3 ? "FAILED" : "PENDING", ex.getClass().getSimpleName(), retries, job.id());
            }
        }
    }

    private List<String> targetTokens(String recipient) {
        if (recipient.equals("ALL_ACTIVE_MEMBERS")) {
            return jdbc.query("""
                SELECT DISTINCT d.device_token FROM member_device_tokens d
                JOIN clients c ON c.id=d.client_id
                WHERE d.is_active=true AND c.status='ACTIVO' AND c.consent_app=true
            """, (r, n) -> r.getString(1));
        }
        if (recipient.equals("ALL_MEMBERS")) {
            return jdbc.query("SELECT DISTINCT d.device_token FROM member_device_tokens d JOIN clients c ON c.id=d.client_id WHERE d.is_active=true AND c.status='ACTIVO' AND c.consent_app=true",
                    (r, n) -> r.getString(1));
        }
        return jdbc.query("SELECT d.device_token FROM member_device_tokens d JOIN clients c ON c.id=d.client_id WHERE d.client_id=? AND d.is_active=true AND c.status='ACTIVO' AND c.consent_app=true",
                (r, n) -> r.getString(1), UUID.fromString(recipient));
    }

    private void markSent(UUID id) {
        jdbc.update("UPDATE notification_outbox SET status='SENT', processed_at_utc=CURRENT_TIMESTAMP, error_message=NULL WHERE id=?", id);
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
