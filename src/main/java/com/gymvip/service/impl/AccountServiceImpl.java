package com.gymvip.service.impl;

import com.gymvip.dto.request.CreateStaffAccountRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.request.UpdateStaffStatusRequest;
import com.gymvip.dto.request.UpdateStaffProfileRequest;
import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.Account;
import com.gymvip.enums.Role;
import com.gymvip.exception.AccountNotFoundException;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.LastAdminProtectionException;
import com.gymvip.mapper.AccountMapper;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AccountService;
import com.gymvip.service.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AccountServiceImpl(AccountRepository accountRepository,
                              SessionRepository sessionRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              AuditService auditService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffAccountResponse> listStaffAccounts() {
        return accountRepository.findStaffAccounts().stream()
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountResponse getById(UUID id) {
        return accountRepository.findById(id)
                .filter(this::isStaff)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + id));
    }

    @Override
    @Transactional
    public StaffAccountResponse createStaffAccount(CreateStaffAccountRequest request, String actorEmail) {
        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe una cuenta de personal con el correo: " + normalizedEmail);
        }

        String targetRole = request.role() != null ? request.role().trim().toUpperCase() : "";
        if (!targetRole.equals(Role.ADMIN.name()) && !targetRole.equals(Role.GERENTE.name()) && !targetRole.equals(Role.RECEPCION.name()) && !targetRole.equals(Role.AUDITOR.name())) {
            throw new IllegalArgumentException("Rol no autorizado para personal del gimnasio: " + targetRole + ". Roles válidos: ADMIN, GERENTE, RECEPCION, AUDITOR");
        }

        Account account = new Account(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                Set.of(targetRole),
                "ACTIVO",
                false
        );
        account.setFirstName(request.firstName() == null ? null : request.firstName().trim());
        account.setLastName(request.lastName() == null ? null : request.lastName().trim());
        account.setPhone(request.phone() == null ? null : request.phone().trim());

        Account saved = accountRepository.save(account);

        auditService.recordEvent(
                actorEmail,
                "CREATE_STAFF_ACCOUNT",
                "Account",
                saved.getId().toString(),
                "Cuenta de personal creada: " + saved.getEmail() + " con rol " + targetRole
        );

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StaffAccountResponse updateRole(UUID id, UpdateStaffRoleRequest request, String actorEmail) {
        Account account = requireStaff(id);

        String targetRole = request.role() != null ? request.role().trim().toUpperCase() : "";
        if (!targetRole.equals(Role.ADMIN.name()) && !targetRole.equals(Role.GERENTE.name()) && !targetRole.equals(Role.RECEPCION.name()) && !targetRole.equals(Role.AUDITOR.name())) {
            throw new IllegalArgumentException("Rol no autorizado para personal: " + targetRole + ". Roles válidos: ADMIN, GERENTE, RECEPCION, AUDITOR");
        }

        boolean currentlyAdmin = account.getRoles().contains("ADMIN");
        if (currentlyAdmin && !targetRole.equals("ADMIN")) {
            long activeAdmins = accountRepository.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no es posible revocar los privilegios del único Administrador activo.");
            }
        }

        account.setRoles(new HashSet<>(Set.of(targetRole)));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        // Revocar inmediatamente cualquier sesión activa previa con roles obsoletos
        sessionRepository.deleteByAccountId(id);

        auditService.recordEvent(
                actorEmail,
                "UPDATE_STAFF_ROLE",
                "Account",
                saved.getId().toString(),
                "Rol actualizado a " + targetRole + " para la cuenta: " + saved.getEmail()
        );

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StaffAccountResponse updateStatus(UUID id, UpdateStaffStatusRequest request, String actorEmail) {
        Account account = requireStaff(id);

        String targetStatus = request.status() != null ? request.status().trim().toUpperCase() : "";
        if (!targetStatus.equals("ACTIVO") && !targetStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido: " + targetStatus + ". Valores permitidos: ACTIVO, INACTIVO");
        }

        boolean currentlyAdmin = account.getRoles().contains("ADMIN");
        if (currentlyAdmin && "INACTIVO".equals(targetStatus) && "ACTIVO".equalsIgnoreCase(account.getStatus())) {
            long activeAdmins = accountRepository.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no es posible desactivar al único Administrador activo del sistema.");
            }
        }

        account.setStatus(targetStatus);
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        if ("INACTIVO".equals(targetStatus)) {
            sessionRepository.deleteByAccountId(id);
        }

        auditService.recordEvent(
                actorEmail,
                "UPDATE_STAFF_STATUS",
                "Account",
                saved.getId().toString(),
                "Estado de la cuenta " + saved.getEmail() + " modificado a: " + targetStatus
        );

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StaffAccountResponse updateProfile(UUID id, UpdateStaffProfileRequest request, String actorEmail) {
        Account account = requireStaff(id);
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        boolean emailChanged = !account.getEmail().equalsIgnoreCase(email);
        if (emailChanged && accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo ya está registrado.");
        }
        account.setEmail(email);
        account.setFirstName(request.firstName() == null ? null : request.firstName().trim());
        account.setLastName(request.lastName() == null ? null : request.lastName().trim());
        account.setPhone(request.phone() == null ? null : request.phone().trim());
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        if (emailChanged) sessionRepository.deleteByAccountId(id);
        auditService.recordEvent(actorEmail, "UPDATE_STAFF_PROFILE", "Account", id.toString(),
                "Datos del colaborador actualizados; correo modificado: " + emailChanged);
        return accountMapper.toResponse(saved);
    }

    private boolean isStaff(Account account) {
        return account.getRoles().stream().anyMatch(role ->
                role.equals("ADMIN") || role.equals("GERENTE") || role.equals("RECEPCION") || role.equals("AUDITOR"));
    }

    private Account requireStaff(UUID id) {
        return accountRepository.findById(id).filter(this::isStaff)
                .orElseThrow(() -> new AccountNotFoundException("Colaborador no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public void revokeSessions(UUID id, String actorEmail) {
        Account account = requireStaff(id);

        sessionRepository.deleteByAccountId(id);

        auditService.recordEvent(
                actorEmail,
                "REVOKE_SESSIONS",
                "Account",
                account.getId().toString(),
                "Sesiones revocadas para la cuenta: " + account.getEmail()
        );
    }
}
