package com.gymvip.service;

import com.google.firebase.messaging.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PushOutboxWorker {
    private final JdbcTemplate jdbc;
    private final FirebaseMessaging messaging;
    private final boolean enabled;
    public PushOutboxWorker(JdbcTemplate jdbc, ObjectProvider<FirebaseMessaging> messaging,
                            @Value("${comms.push.enabled:true}") boolean enabled) {
        this.jdbc=jdbc; this.messaging=messaging.getIfAvailable(); this.enabled=enabled;
    }
    record Pending(UUID id,String key,String recipient,String itemId,UUID owner,String session,int retries) {}
    record Binding(String token,UUID clientId,UUID accountId,String session) {}

    @Scheduled(fixedDelayString="${PUSH_OUTBOX_POLL_MS:15000}")
    @Transactional
    public void process() {
        if (!enabled || messaging==null) return;
        jdbc.update("UPDATE notification_outbox SET status='SKIPPED',error_message='Push caducado; aviso conservado en bandeja',processed_at_utc=CURRENT_TIMESTAMP WHERE channel='PUSH' AND status='PENDING' AND created_at_utc<CURRENT_TIMESTAMP-INTERVAL '24 hours'");
        List<Pending> jobs=jdbc.query("""
            SELECT id,idempotency_key,recipient,inbox_item_id,owner_client_id,binding_session_token,retry_count
            FROM notification_outbox WHERE channel='PUSH' AND status='PENDING'
            AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP)
            ORDER BY created_at_utc LIMIT 20 FOR UPDATE SKIP LOCKED
            """,(r,n)->new Pending(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),
                r.getObject(5,UUID.class),r.getString(6),r.getInt(7)));
        for (Pending candidate:jobs) {
            Pending job=candidate;
            // Preserve Acosta's order updates; they open the authenticated orders view.
            if (job.itemId()==null && job.key().startsWith("order_notice_"))
                job=new Pending(job.id(),job.key(),job.recipient(),"ORDERS",job.owner(),job.session(),job.retries());
            if (job.itemId()==null) { skip(job,"Sin aviso de destino verificable"); continue; }
            if (job.owner()==null) {
                for (Binding binding:bindings(job)) {
                    jdbc.update("""
                        INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status,
                            inbox_item_id,owner_client_id,binding_session_token)
                        VALUES (?,?,'PUSH',?,'GYM VIP','Nuevo aviso disponible','PENDING',?,?,?)
                        ON CONFLICT(idempotency_key) DO NOTHING
                        """,UUID.randomUUID(),"push_"+digest(job.key()+":"+binding.token()+":"+binding.session()),
                        binding.token(),job.itemId(),binding.clientId(),binding.session());
                }
                jdbc.update("UPDATE notification_outbox SET status='EXPANDED',processed_at_utc=CURRENT_TIMESTAMP WHERE id=?",job.id());
                continue;
            }
            var targets=bindings(job);
            if (targets.isEmpty()) { skip(job,"Sin sesión, dispositivo, consentimiento o aviso vigente"); continue; }
            Binding binding=targets.getFirst();
            try {
                // Data-only: Android validates the account even when the app is backgrounded.
                String providerId=messaging.send(Message.builder().setToken(binding.token())
                    .putData("type",job.itemId().equals("ORDERS") ? "GYMVIP_ORDERS":"GYMVIP_INBOX").putData("itemId",job.itemId())
                    .putData("accountId",binding.accountId().toString())
                    .putData("sessionTag",digest(binding.session()))
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(24*60*60*1000L).build()).build());
                jdbc.update("UPDATE notification_outbox SET status='ACCEPTED',provider_message_id=?,processed_at_utc=CURRENT_TIMESTAMP,error_message=NULL WHERE id=?",providerId,job.id());
            } catch (Exception error) {
                boolean invalid=error instanceof FirebaseMessagingException f && f.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED;
                if (invalid) jdbc.update("UPDATE member_device_tokens SET is_active=false,updated_at_utc=CURRENT_TIMESTAMP WHERE device_token=? AND client_id=? AND session_token=?",binding.token(),binding.clientId(),binding.session());
                int retries=job.retries()+1;
                boolean terminal=invalid || retries>=3;
                jdbc.update("""
                    UPDATE notification_outbox SET retry_count=?,status=?,error_message=?,
                        processed_at_utc=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                        next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second') WHERE id=?
                    """,retries,terminal ? "FAILED":"PENDING",invalid ? "UNREGISTERED":error.getClass().getSimpleName(),
                    terminal,30*(1 << Math.min(retries-1,3)),job.id());
            }
        }
    }

    private List<Binding> bindings(Pending job) {
        return jdbc.query("""
            SELECT d.device_token,c.id,c.account_id,d.session_token FROM member_device_tokens d
            JOIN clients c ON c.id=d.client_id JOIN accounts a ON a.id=c.account_id
            JOIN sessions s ON s.token=d.session_token AND s.account_id=c.account_id
            WHERE d.is_active=true AND c.status='ACTIVO' AND a.status='ACTIVO' AND c.consent_app=true
            AND s.expires_at>CURRENT_TIMESTAMP
            AND (? IN ('ALL_MEMBERS','ALL_ACTIVE_MEMBERS') OR c.id::text=? OR d.device_token=?)
            AND (CAST(? AS uuid) IS NULL OR (c.id=CAST(? AS uuid) AND d.session_token=? AND d.device_token=?))
            AND (EXISTS(SELECT 1 FROM sent_reminder_dispatches n WHERE n.id::text=? AND n.client_id=c.id AND n.channel='APP' AND n.status='DISPONIBLE')
                OR EXISTS(SELECT 1 FROM gym_announcements g WHERE g.id::text=? AND g.is_active=true) OR ?='ORDERS')
            """,(r,n)->new Binding(r.getString(1),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4)),
            job.recipient(),job.recipient(),job.recipient(),job.owner(),job.owner(),job.session(),job.recipient(),job.itemId(),job.itemId(),job.itemId());
    }
    private void skip(Pending job,String reason) {
        jdbc.update("UPDATE notification_outbox SET status='SKIPPED',error_message=?,processed_at_utc=CURRENT_TIMESTAMP WHERE id=?",reason,job.id());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
