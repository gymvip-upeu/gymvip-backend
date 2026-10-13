package com.gymvip.identity.application;

import com.gymvip.audit.application.AuditService;
import com.gymvip.identity.domain.Account;
import com.gymvip.identity.infrastructure.AccountRepository;
import com.gymvip.shared.exceptions.ConflictException;
import com.gymvip.shared.exceptions.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountManagementService {

    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AccountManagementService(AccountRepository accountRepository,
                                  AuthService authService,
                                  PasswordEncoder passwordEncoder,
                                  AuditService auditService) {
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public record StaffAccountResponse(
            UUID id,
            String email,
            Set<String> roles,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static StaffAccountResponse from(Account a) {
            return new StaffAccountResponse(
                    a.getId(),
                    a.getEmail(),
                    a.getRoles(),
                    a.getStatus(),
                    a.getCreatedAt(),
                    a.getUpdatedAt()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> listStaffAccounts() {
        return accountRepository.findAll().stream()
                .map(StaffAccountResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StaffAccountResponse getById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + id));
        return StaffAccountResponse.from(account);
    }

    @Transactional
    public StaffAccountResponse createStaffAccount(String email, String rawPassword, String role, String actorEmail) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("El correo institucional es obligatorio y debe ser válido");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe contener al menos 8 caracteres");
        }

        String normRole = role != null ? role.trim().toUpperCase() : "";
        if (!normRole.equals("ADMIN") && !normRole.equals("RECEPCION")) {
            throw new IllegalArgumentException("Rol de personal no autorizado. Valores permitidos: ADMIN, RECEPCION");
        }

        String normEmail = email.trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(normEmail)) {
            throw new ConflictException("Ya existe una cuenta registrada con el correo: " + normEmail);
        }

        Account account = new Account(
                normEmail,
                passwordEncoder.encode(rawPassword),
                Set.of(normRole)
        );
        account.setStatus("ACTIVO");
        Account saved = accountRepository.save(account);

        auditService.recordEvent(actorEmail, "CREATE_STAFF_ACCOUNT", "Account", saved.getId().toString(),
                "Cuenta de personal creada para: " + normEmail + " con rol " + normRole);

        return StaffAccountResponse.from(saved);
    }

    @Transactional
    public StaffAccountResponse updateRole(UUID accountId, String newRole, String actorEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + accountId));

        String normRole = newRole != null ? newRole.trim().toUpperCase() : "";
        if (!normRole.equals("ADMIN") && !normRole.equals("RECEPCION")) {
            throw new IllegalArgumentException("Rol no permitido. Valores autorizados: ADMIN, RECEPCION");
        }

        boolean wasAdmin = account.getRoles().contains("ADMIN") || account.getRoles().contains("SUPERADMIN");
        boolean willBeAdmin = normRole.equals("ADMIN");

        if (wasAdmin && !willBeAdmin && "ACTIVO".equalsIgnoreCase(account.getStatus())) {
            if (accountRepository.countActiveAdmins() <= 1) {
                throw new ConflictException("Operación denegada: no se puede degradar al último administrador activo del sistema");
            }
        }

        account.setRoles(Set.of(normRole));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        // Revocar sesiones activas para que la nueva autorización tome efecto inmediato
        authService.revokeSessionsForAccount(accountId);

        auditService.recordEvent(actorEmail, "UPDATE_STAFF_ROLE", "Account", saved.getId().toString(),
                "Rol de cuenta " + saved.getEmail() + " actualizado a " + normRole);

        return StaffAccountResponse.from(saved);
    }

    @Transactional
    public StaffAccountResponse updateStatus(UUID accountId, String newStatus, String actorEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + accountId));

        String normStatus = newStatus != null ? newStatus.trim().toUpperCase() : "";
        if (!normStatus.equals("ACTIVO") && !normStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido. Valores permitidos: ACTIVO, INACTIVO");
        }

        boolean wasAdmin = account.getRoles().contains("ADMIN") || account.getRoles().contains("SUPERADMIN");
        if (wasAdmin && "INACTIVO".equals(normStatus) && "ACTIVO".equalsIgnoreCase(account.getStatus())) {
            if (accountRepository.countActiveAdmins() <= 1) {
                throw new ConflictException("Operación denegada: no se puede inactivar al último administrador activo del sistema");
            }
        }

        account.setStatus(normStatus);
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        if ("INACTIVO".equals(normStatus)) {
            authService.revokeSessionsForAccount(accountId);
        }

        auditService.recordEvent(actorEmail, "UPDATE_ACCOUNT_STATUS", "Account", saved.getId().toString(),
                "Estado de cuenta " + saved.getEmail() + " cambiado a " + normStatus);

        return StaffAccountResponse.from(saved);
    }

    @Transactional
    public void revokeSessions(UUID accountId, String actorEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + accountId));

        authService.revokeSessionsForAccount(accountId);

        auditService.recordEvent(actorEmail, "REVOKE_STAFF_SESSIONS", "Account", account.getId().toString(),
                "Sesiones revocadas para la cuenta: " + account.getEmail());
    }
}
