package com.gymvip.saas.service;

import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.service.AccountPasswordPolicy;
import com.gymvip.service.AuditService;
import com.gymvip.service.MemberAccessService.SecurityEmail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class CompanyInvitationService {
    public static final String TEMPLATE = "company_temporary_password";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public CompanyInvitationService(JdbcTemplate jdbc, PasswordEncoder encoder, ApplicationEventPublisher events, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.events = events;
        this.audit = audit;
    }

    @Transactional
    public String send(UUID requestId, String actor, boolean resend) {
        var request = jdbc.queryForList("""
                SELECT r.status, r.contact_email, a.id AS account_id
                FROM organization_requests r
                JOIN accounts a ON a.organization_id = r.approved_organization_id AND LOWER(a.email) = LOWER(r.contact_email)
                WHERE r.id = ? AND (a.status = 'PENDIENTE_ACTIVACION'
                    OR (a.status = 'ACTIVO' AND a.first_access_password AND a.must_change_password))
                FOR UPDATE OF r, a
                """, requestId).stream().findFirst().orElse(null);
        if (request == null) {
            if (resend) throw new ConflictException("La empresa no tiene una cuenta pendiente de activar.");
            return "NOT_REQUIRED";
        }
        if (!resend) {
            String previous = jdbc.queryForList("SELECT email_status FROM company_activation_invitations WHERE request_id = ?",
                    String.class, requestId).stream().findFirst().orElse(null);
            if (previous != null) return previous;
        }
        if (resend && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM company_activation_invitations WHERE request_id = ?
                    AND email_status = 'PENDING' AND expires_at > NOW() + INTERVAL '23 hours 59 minutes')
                """, Boolean.class, requestId))) return "PENDING";
        UUID id = UUID.randomUUID();
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        UUID accountId = (UUID) request.get("account_id");
        String temporaryPassword = token.substring(0, 28) + "Aa1!";
        jdbc.update("""
                INSERT INTO company_activation_invitations (id, request_id, account_id, token_hash, expires_at, sent_by, delivery_mode)
                VALUES (?, ?, ?, ?, NOW() + INTERVAL '24 hours', ?, 'TEMPORARY_PASSWORD')
                ON CONFLICT (request_id) DO UPDATE SET id = EXCLUDED.id, token_hash = EXCLUDED.token_hash,
                    account_id = EXCLUDED.account_id, expires_at = EXCLUDED.expires_at, consumed_at = NULL,
                    sent_by = EXCLUDED.sent_by, email_status = 'PENDING', provider_id = NULL,
                    delivery_mode = EXCLUDED.delivery_mode
                """, id, requestId, accountId, hash(token), actor);
        jdbc.update("""
                UPDATE accounts SET password_hash=?, status='ACTIVO', must_change_password=TRUE,
                    first_access_password=TRUE, first_access_password_consumed_at=NULL, first_access_original_hash=NULL,
                    temporary_password_expires_at=(NOW() AT TIME ZONE 'UTC') + INTERVAL '24 hours'
                WHERE id=?
                """, encoder.encode(temporaryPassword), accountId);
        jdbc.update("DELETE FROM sessions WHERE account_id=?", accountId);
        String email = (String) request.get("contact_email");
        events.publishEvent(new SecurityEmail(id, email, "Activa tu empresa en GYM VIP",
                TEMPLATE, Map.of("username", email, "temporaryPassword", temporaryPassword, "idempotencyKey", "company-access-" + id)));
        audit.recordEvent(actor, resend ? "RESEND_COMPANY_INVITATION" : "SEND_COMPANY_INVITATION",
                "OrganizationRequest", requestId.toString(), "Invitación en cola");
        return "PENDING";
    }

    @Transactional
    public void activate(String token, String password) {
        AccountPasswordPolicy.validate(password);
        String fingerprint = hash(token);
        UUID id = jdbc.queryForList("SELECT request_id FROM company_activation_invitations WHERE token_hash = ?",
                UUID.class, fingerprint).stream().findFirst().orElseThrow(CompanyInvitationService::invalidInvitation);
        jdbc.queryForObject("SELECT id FROM organization_requests WHERE id = ? FOR UPDATE", UUID.class, id);
        var invitations = jdbc.queryForList("""
                SELECT request_id, account_id FROM company_activation_invitations
                WHERE request_id = ? AND token_hash = ? AND consumed_at IS NULL AND expires_at > NOW()
                  AND delivery_mode='LINK'
                FOR UPDATE
                """, id, fingerprint);
        if (invitations.isEmpty()) throw invalidInvitation();
        var invitation = invitations.getFirst();
        UUID requestId = (UUID) invitation.get("request_id");
        if (jdbc.update("""
                UPDATE accounts AS a SET password_hash = ?, status = 'ACTIVO', must_change_password = FALSE,
                    temporary_password_expires_at = NULL
                FROM organization_requests r
                WHERE r.id = ? AND a.id = ? AND a.organization_id = r.approved_organization_id
                    AND a.status = 'PENDIENTE_ACTIVACION'
                """, encoder.encode(password), requestId, invitation.get("account_id")) != 1) throw invalidInvitation();
        jdbc.update("UPDATE company_activation_invitations SET consumed_at = NOW() WHERE request_id = ?", requestId);
        jdbc.update("UPDATE organization_requests SET status = 'ACTIVADA', updated_at_utc = NOW() WHERE id = ?", requestId);
    }

    private static ResourceNotFoundException invalidInvitation() {
        return new ResourceNotFoundException("La invitación venció, ya fue utilizada o no es válida.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
