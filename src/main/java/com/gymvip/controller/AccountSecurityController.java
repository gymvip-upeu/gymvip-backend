package com.gymvip.controller;

import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.entity.Account;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.PermissionCatalog;
import com.gymvip.security.TotpService;
import com.gymvip.service.AccountService;
import com.gymvip.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/security")
public class AccountSecurityController {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final AuditService auditService;

    public AccountSecurityController(AccountRepository accountRepository,
                                     SessionRepository sessionRepository,
                                     TotpService totpService,
                                     PasswordEncoder passwordEncoder,
                                     AccountService accountService,
                                     AuditService auditService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.auditService = auditService;
    }

    @PostMapping("/mfa/enroll")
    public ResponseEntity<?> enrollTotp(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();

        String secret = totpService.generateSecret();
        List<String> recoveryCodes = totpService.generateRecoveryCodes(6);
        String hashedCodes = recoveryCodes.stream()
                .map(totpService::hashRecoveryCode)
                .collect(java.util.stream.Collectors.joining(","));

        account.setTotpSecret(secret);
        account.setMfaRecoveryHashes(hashedCodes);
        accountRepository.save(account);

        auditService.recordEvent(account.getEmail(), "MFA_TOTP_ENROLL_INITIATED", "Account", account.getId().toString(),
                "Generación de secreto TOTP y 6 códigos de recuperación");

        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpauthUri", totpService.buildProvisioningUri(account.getEmail(), secret),
                "recoveryCodes", recoveryCodes
        ));
    }

    public record VerifyMfaRequest(String code, String password) {}

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyAndEnableTotp(@RequestBody VerifyMfaRequest request, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();
        if (request == null || !totpService.verifyCode(account.getTotpSecret(), request.code())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Código TOTP de 6 dígitos inválido o expirado."));
        }
        account.setMfaEnabled(true);
        account.setMfaVerifiedAt(LocalDateTime.now());
        accountRepository.save(account);
        auditService.recordEvent(account.getEmail(), "MFA_TOTP_ENABLED", "Account", account.getId().toString(),
                "Autenticación multifactor TOTP activada correctamente");
        return ResponseEntity.ok(Map.of("mfaEnabled", true, "verifiedAt", account.getMfaVerifiedAt()));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableTotp(@RequestBody VerifyMfaRequest request, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();
        if (request == null || request.password() == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Reautenticación obligatoria fallida.");
        }
        boolean validOtp = totpService.verifyCode(account.getTotpSecret(), request.code());
        boolean validRecovery = false;
        if (!validOtp && request.code() != null && account.getMfaRecoveryHashes() != null) {
            String hashed = totpService.hashRecoveryCode(request.code());
            List<String> current = new ArrayList<>(Arrays.asList(account.getMfaRecoveryHashes().split(",")));
            if (current.remove(hashed)) {
                validRecovery = true;
                account.setMfaRecoveryHashes(String.join(",", current));
            }
        }
        if (!validOtp && !validRecovery) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ingresa un código TOTP o código de recuperación válido."));
        }
        account.setMfaEnabled(false);
        account.setTotpSecret(null);
        accountRepository.save(account);
        auditService.recordEvent(account.getEmail(), "MFA_TOTP_DISABLED", "Account", account.getId().toString(),
                "Desactivación controlada de TOTP tras reautenticación");
        return ResponseEntity.ok(Map.of("mfaEnabled", false));
    }

    public record PrivilegedMigrationRequest(UUID accountId, String targetRole, String currentPassword, String reason) {}

    /**
     * Migración controlada ADMIN -> SUPER_ADMIN o ADMIN_EMPRESA con reautenticación y revocación de sesiones.
     */
    @PostMapping("/privileged/migrate-admin-role")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<?> controlledAdminMigration(@RequestBody PrivilegedMigrationRequest request,
                                                      Authentication authentication) {
        Account actor = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (actor == null || request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), actor.getPasswordHash())) {
            throw new InvalidCredentialsException("Reautenticación obligatoria fallida para migración de rol privilegiado.");
        }
        String target = PermissionCatalog.normalizeRole(request.targetRole());
        if (!Set.of("SUPER_ADMIN", "ADMIN_EMPRESA", "GERENTE_GENERAL").contains(target)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Destino de migración no permitido: " + target));
        }
        var updated = accountService.updateRole(request.accountId(), new UpdateStaffRoleRequest(target), actor.getEmail());
        sessionRepository.deleteByAccountId(request.accountId());
        auditService.recordEvent(actor.getEmail(), "CONTROLLED_ADMIN_MIGRATION", "Account",
                request.accountId().toString(),
                "Migración hacia " + target + " | Motivo: " + (request.reason() != null ? request.reason() : "N/A"));
        return ResponseEntity.ok(updated);
    }
}
