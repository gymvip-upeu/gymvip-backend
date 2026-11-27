package com.gymvip.controller;

import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.entity.Account;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.PermissionCatalog;
import com.gymvip.security.TotpService;
import com.gymvip.security.StaffAccessPolicy;
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

        if (account.isMfaEnabled()) throw StaffAccessPolicy.denied();
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
        if (PermissionCatalog.isSuperAdmin(account.getRoles())) sessionRepository.deleteByAccountId(account.getId());
        auditService.recordEvent(account.getEmail(), "MFA_TOTP_ENABLED", "Account", account.getId().toString(),
                "Autenticación multifactor TOTP activada correctamente");
        return ResponseEntity.ok(Map.of("mfaEnabled", true, "verifiedAt", account.getMfaVerifiedAt()));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableTotp(@RequestBody VerifyMfaRequest request, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) return ResponseEntity.status(404).build();
        if (PermissionCatalog.isSuperAdmin(account.getRoles())) throw StaffAccessPolicy.denied();
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

    public record PrivilegedMigrationRequest(UUID accountId, String targetRole, String currentPassword,
                                             String reason, String mfaCode) {}

    @PostMapping("/privileged/migrate-admin-role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> controlledAdminMigration(@RequestBody PrivilegedMigrationRequest request,
                                                       Authentication authentication) {
        Account actor = verifyPlatformOperator(authentication, request.currentPassword(), request.mfaCode());
        String target = PermissionCatalog.normalizeRole(request.targetRole());
        if (!Set.of("SUPER_ADMIN", "SOPORTE_PLATAFORMA", "ADMIN_EMPRESA").contains(target)) {
            throw new IllegalArgumentException("El destino requiere un rol de plataforma o un administrador de empresa.");
        }
        return ResponseEntity.ok(changePrivilegedRole(actor, request.accountId(), target, request.reason()));
    }

    public record PrivilegedRevocationRequest(UUID accountId, String replacementRole, String currentPassword,
                                              String mfaCode, String reason) {}

    @PostMapping("/privileged/revoke-role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> revokePrivilegedRole(@RequestBody PrivilegedRevocationRequest request, Authentication authentication) {
        Account actor = verifyPlatformOperator(authentication, request.currentPassword(), request.mfaCode());
        String target = PermissionCatalog.normalizeRole(request.replacementRole());
        if (!PermissionCatalog.isAssignableStaffRole(target) ||
                Set.of("SUPER_ADMIN", "SOPORTE_PLATAFORMA", "ADMIN_EMPRESA").contains(PermissionCatalog.canonicalRole(target))) {
            throw new IllegalArgumentException("Selecciona un rol operativo para revocar estos privilegios.");
        }
        return ResponseEntity.ok(changePrivilegedRole(actor, request.accountId(), target, request.reason()));
    }

    private Account verifyPlatformOperator(Authentication authentication, String password, String code) {
        Account actor = StaffAccessPolicy.actor(accountRepository, authentication == null ? null : authentication.getName());
        if (!PermissionCatalog.isSuperAdmin(actor.getRoles()) || !actor.isMfaEnabled()) throw StaffAccessPolicy.denied();
        if (password == null || !passwordEncoder.matches(password, actor.getPasswordHash()) ||
                !totpService.verifyCode(actor.getTotpSecret(), code)) {
            throw new InvalidCredentialsException("Se requieren contraseña y TOTP válidos.");
        }
        return actor;
    }

    private Object changePrivilegedRole(Account actor, UUID accountId, String target, String reason) {
        if (accountId == null || reason == null || reason.trim().length() < 8 || reason.length() > 400) {
            throw new IllegalArgumentException("Indica una cuenta y un motivo de 8 a 400 caracteres.");
        }
        if (accountId.equals(actor.getId())) throw StaffAccessPolicy.denied();
        var result = accountService.updatePrivilegedRole(accountId, new UpdateStaffRoleRequest(target), actor.getEmail());
        auditService.recordEvent(actor.getEmail(), "CHANGE_PRIVILEGED_ROLE", "Account", accountId.toString(), reason.trim());
        return result;
    }
}
