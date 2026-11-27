package com.gymvip.controller;

import com.gymvip.exception.ConflictException;
import com.gymvip.service.AccountPasswordPolicy;
import com.gymvip.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/first-password")
public class FirstPasswordController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public FirstPasswordController(JdbcTemplate jdbc, PasswordEncoder passwords, AuditService audit) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.audit = audit;
    }

    public record Request(@NotBlank @Size(max = 72) String newPassword) {}

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void complete(@Valid @RequestBody Request request, Authentication actor) {
        AccountPasswordPolicy.validate(request.newPassword());
        var pending = jdbc.query("""
                SELECT id, first_access_original_hash FROM accounts
                WHERE LOWER(email)=LOWER(?) AND status='ACTIVO' AND must_change_password AND first_access_password
                  AND first_access_password_consumed_at IS NOT NULL
                  AND temporary_password_expires_at > (NOW() AT TIME ZONE 'UTC')
                FOR UPDATE
                """, (rs, row) -> new PendingAccess(rs.getObject(1, UUID.class), rs.getString(2)), actor.getName())
                .stream().findFirst().orElseThrow(() -> new ConflictException("Solicita una nueva credencial de primer acceso."));
        if (passwords.matches(request.newPassword(), pending.temporaryHash())) {
            throw new IllegalArgumentException("Elige una contraseña diferente de la temporal.");
        }
        UUID id = pending.id();
        jdbc.update("""
                UPDATE accounts SET password_hash=?, must_change_password=FALSE, first_access_password=FALSE,
                    first_access_password_consumed_at=NULL, first_access_original_hash=NULL,
                    temporary_password_expires_at=NULL
                WHERE id=?
                """, passwords.encode(request.newPassword()), id);
        jdbc.update("""
                UPDATE organization_requests SET status='ACTIVADA', updated_at_utc=NOW()
                WHERE id IN (
                    SELECT request_id FROM company_activation_invitations
                    WHERE account_id=? AND delivery_mode='TEMPORARY_PASSWORD'
                )
                """, id);
        jdbc.update("UPDATE company_activation_invitations SET consumed_at=NOW() WHERE account_id=? AND consumed_at IS NULL", id);
        jdbc.update("DELETE FROM sessions WHERE account_id=?", id);
        audit.recordEvent(actor.getName(), "COMPLETE_FIRST_PASSWORD", "Account", id.toString(), "Primer acceso completado");
    }

    private record PendingAccess(UUID id, String temporaryHash) {}
}
