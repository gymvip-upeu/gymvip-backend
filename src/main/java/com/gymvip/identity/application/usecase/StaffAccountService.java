package com.gymvip.identity.application.usecase;

import com.gymvip.audit.application.port.in.AuditUseCase;
import com.gymvip.identity.application.port.in.StaffAccountUseCase;
import com.gymvip.identity.application.port.out.AccountPersistencePort;
import com.gymvip.identity.application.port.out.SessionPersistencePort;
import com.gymvip.identity.domain.exception.AccountNotFoundException;
import com.gymvip.identity.domain.exception.LastAdminProtectionException;
import com.gymvip.identity.domain.model.Account;
import com.gymvip.shared.exceptions.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StaffAccountService implements StaffAccountUseCase {

    private final AccountPersistencePort accountPersistencePort;
    private final SessionPersistencePort sessionPersistencePort;
    private final PasswordEncoder passwordEncoder;
    private final AuditUseCase auditUseCase;

    public StaffAccountService(AccountPersistencePort accountPersistencePort,
                               SessionPersistencePort sessionPersistencePort,
                               PasswordEncoder passwordEncoder,
                               AuditUseCase auditUseCase) {
        this.accountPersistencePort = accountPersistencePort;
        this.sessionPersistencePort = sessionPersistencePort;
        this.passwordEncoder = passwordEncoder;
        this.auditUseCase = auditUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffAccountDto> listStaffAccounts() {
        return accountPersistencePort.findAll().stream()
                .map(StaffAccountDto::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountDto getById(UUID id) {
        Account account = accountPersistencePort.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + id));
        return StaffAccountDto.from(account);
    }

    @Override
    @Transactional
    public StaffAccountDto createStaffAccount(CreateStaffCommand command, String actorEmail) {
        if (command.email() == null || command.email().isBlank() || !command.email().contains("@")) {
            throw new IllegalArgumentException("El correo institucional es obligatorio y debe ser válido");
        }
        if (command.rawPassword() == null || command.rawPassword().length() < 8) {
            throw new IllegalArgumentException("La contraseña debe contener al menos 8 caracteres");
        }

        String normRole = command.role() != null ? command.role().trim().toUpperCase() : "";
        if (!normRole.equals("ADMIN") && !normRole.equals("RECEPCION")) {
            throw new IllegalArgumentException("Rol de personal no autorizado. Valores permitidos: ADMIN, RECEPCION");
        }

        String normEmail = command.email().trim().toLowerCase();
        if (accountPersistencePort.existsByEmailIgnoreCase(normEmail)) {
            throw new ConflictException("Ya existe una cuenta registrada con el correo: " + normEmail);
        }

        Account account = new Account(
                UUID.randomUUID(),
                normEmail,
                passwordEncoder.encode(command.rawPassword()),
                Set.of(normRole),
                "ACTIVO",
                false
        );

        Account saved = accountPersistencePort.save(account);

        auditUseCase.recordEvent(
                actorEmail,
                "CREATE_STAFF_ACCOUNT",
                "Account",
                saved.getId().toString(),
                "Cuenta de personal creada para: " + normEmail + " con rol " + normRole
        );

        return StaffAccountDto.from(saved);
    }

    @Override
    @Transactional
    public StaffAccountDto updateRole(UUID accountId, String newRole, String actorEmail) {
        Account account = accountPersistencePort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + accountId));

        String normRole = newRole != null ? newRole.trim().toUpperCase() : "";
        if (!normRole.equals("ADMIN") && !normRole.equals("RECEPCION")) {
            throw new IllegalArgumentException("Rol no permitido. Valores autorizados: ADMIN, RECEPCION");
        }

        boolean wasAdmin = account.isAdmin();
        boolean willBeAdmin = normRole.equals("ADMIN");

        if (wasAdmin && !willBeAdmin && account.isActive()) {
            if (accountPersistencePort.countActiveAdmins() <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no se puede degradar al último administrador activo del sistema");
            }
        }

        account.setRoles(Set.of(normRole));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountPersistencePort.save(account);

        // Revocar sesiones activas para que la nueva autorización tome efecto inmediato
        sessionPersistencePort.deleteByAccountId(accountId);

        auditUseCase.recordEvent(
                actorEmail,
                "UPDATE_STAFF_ROLE",
                "Account",
                saved.getId().toString(),
                "Rol de cuenta " + saved.getEmail() + " actualizado a " + normRole
        );

        return StaffAccountDto.from(saved);
    }

    @Override
    @Transactional
    public StaffAccountDto updateStatus(UUID accountId, String newStatus, String actorEmail) {
        Account account = accountPersistencePort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + accountId));

        String normStatus = newStatus != null ? newStatus.trim().toUpperCase() : "";
        if (!normStatus.equals("ACTIVO") && !normStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido. Valores permitidos: ACTIVO, INACTIVO");
        }

        boolean wasAdmin = account.isAdmin();
        if (wasAdmin && "INACTIVO".equals(normStatus) && account.isActive()) {
            if (accountPersistencePort.countActiveAdmins() <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no se puede inactivar al último administrador activo del sistema");
            }
        }

        account.setStatus(normStatus);
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountPersistencePort.save(account);

        if ("INACTIVO".equals(normStatus)) {
            sessionPersistencePort.deleteByAccountId(accountId);
        }

        auditUseCase.recordEvent(
                actorEmail,
                "UPDATE_ACCOUNT_STATUS",
                "Account",
                saved.getId().toString(),
                "Estado de cuenta " + saved.getEmail() + " cambiado a " + normStatus
        );

        return StaffAccountDto.from(saved);
    }

    @Override
    @Transactional
    public void revokeSessions(UUID accountId, String actorEmail) {
        Account account = accountPersistencePort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + accountId));

        sessionPersistencePort.deleteByAccountId(accountId);

        auditUseCase.recordEvent(
                actorEmail,
                "REVOKE_STAFF_SESSIONS",
                "Account",
                account.getId().toString(),
                "Sesiones revocadas para la cuenta: " + account.getEmail()
        );
    }
}
