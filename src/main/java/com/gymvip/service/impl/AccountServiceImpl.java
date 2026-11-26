package com.gymvip.service.impl;

import com.gymvip.dto.request.CreateStaffAccountRequest;
import com.gymvip.dto.request.UpdateStaffProfileRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.request.UpdateStaffStatusRequest;
import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.AuditEvent;
import com.gymvip.exception.AccountNotFoundException;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.LastAdminProtectionException;
import com.gymvip.mapper.AccountMapper;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.AuditEventRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.PermissionCatalog;
import com.gymvip.service.AccountService;
import com.gymvip.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;

    public AccountServiceImpl(AccountRepository accountRepository,
                              SessionRepository sessionRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              AuditService auditService) {
        this(accountRepository, sessionRepository, accountMapper, passwordEncoder, auditService, null);
    }

    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository,
                              SessionRepository sessionRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              AuditService auditService,
                              AuditEventRepository auditEventRepository) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.auditEventRepository = auditEventRepository;
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
        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : "";
        if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe una cuenta de personal con el correo: " + normalizedEmail);
        }

        String targetRole = PermissionCatalog.normalizeRole(request.role());
        if (!PermissionCatalog.isAssignableStaffRole(targetRole)) {
            throw new IllegalArgumentException("Rol no autorizado para personal del gimnasio: " + targetRole);
        }

        ensureActorCanAssignRole(actorEmail, targetRole);

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

        String targetRole = PermissionCatalog.normalizeRole(request.role());
        if (!PermissionCatalog.isAssignableStaffRole(targetRole)) {
            throw new IllegalArgumentException("Rol no autorizado para personal: " + targetRole);
        }

        boolean sameAccount = actorEmail != null && actorEmail.trim().equalsIgnoreCase(account.getEmail());
        boolean currentlyPrivileged = PermissionCatalog.isPlatformOrLegacyAdmin(account.getRoles());
        boolean willRemainPrivileged = "ADMIN".equals(targetRole) || "SUPER_ADMIN".equals(targetRole);

        if (sameAccount && currentlyPrivileged && !willRemainPrivileged) {
            throw new LastAdminProtectionException("Operación denegada: no puedes autodegradar tu propia cuenta privilegiada.");
        }
        if (sameAccount && !currentlyPrivileged && ("SUPER_ADMIN".equals(targetRole) || "ADMIN".equals(targetRole))) {
            throw new LastAdminProtectionException("Operación denegada: autoelevación de privilegios prohibida.");
        }

        ensureActorCanModifyTargetAccount(actorEmail, account);
        ensureActorCanAssignRole(actorEmail, targetRole);

        if (currentlyPrivileged && !willRemainPrivileged) {
            long activeAdmins = accountRepository.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no es posible revocar los privilegios del único Administrador activo.");
            }
        }
        if (PermissionCatalog.isSuperAdmin(account.getRoles()) && !"SUPER_ADMIN".equals(targetRole)) {
            long activeSuperAdmins = accountRepository.countActiveSuperAdmins();
            if (activeSuperAdmins <= 1 && accountRepository.countActiveAdmins() <= 1) {
                throw new LastAdminProtectionException("Operación denegada: no es posible degradar al único Super Administrador autorizado.");
            }
        }

        account.setRoles(new HashSet<>(Set.of(targetRole)));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

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

        String targetStatus = request.status() != null ? request.status().trim().toUpperCase(Locale.ROOT) : "";
        if (!targetStatus.equals("ACTIVO") && !targetStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido: " + targetStatus + ". Valores permitidos: ACTIVO, INACTIVO");
        }

        if ("INACTIVO".equals(targetStatus) && actorEmail != null && actorEmail.trim().equalsIgnoreCase(account.getEmail())) {
            throw new LastAdminProtectionException("Operación denegada: no puedes autoinactivar ni autoeliminar tu propia cuenta.");
        }

        ensureActorCanModifyTargetAccount(actorEmail, account);

        boolean currentlyAdmin = PermissionCatalog.isPlatformOrLegacyAdmin(account.getRoles());
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
        ensureActorCanModifyTargetAccount(actorEmail, account);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
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
        if (emailChanged) {
            sessionRepository.deleteByAccountId(id);
        }
        auditService.recordEvent(actorEmail, "UPDATE_STAFF_PROFILE", "Account", id.toString(),
                "Datos del colaborador actualizados; correo modificado: " + emailChanged);
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void revokeSessions(UUID id, String actorEmail) {
        Account account = requireStaff(id);
        ensureActorCanModifyTargetAccount(actorEmail, account);

        sessionRepository.deleteByAccountId(id);

        auditService.recordEvent(
                actorEmail,
                "REVOKE_SESSIONS",
                "Account",
                account.getId().toString(),
                "Sesiones revocadas para la cuenta: " + account.getEmail()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> listAccountAuditHistory(UUID accountId) {
        if (auditEventRepository == null) {
            return List.of();
        }
        return auditEventRepository.findTop50ByEntityNameAndEntityIdOrderByTimestampUtcDesc("Account", accountId.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> listRecentStaffAuditEvents() {
        if (auditEventRepository == null) {
            return List.of();
        }
        return auditEventRepository.findTop100ByEntityNameOrderByTimestampUtcDesc("Account");
    }

    private void ensureActorCanAssignRole(String actorEmail, String targetRole) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return;
        }
        Account actor = accountRepository.findByEmailIgnoreCase(actorEmail.trim()).orElse(null);
        if (actor == null) {
            return;
        }
        Set<String> actorRoles = actor.getRoles();
        if ("SUPER_ADMIN".equals(targetRole) && !PermissionCatalog.isSuperAdmin(actorRoles)) {
            throw new LastAdminProtectionException("Operación denegada: solo SUPER_ADMIN autorizado puede otorgar el rol SUPER_ADMIN.");
        }
        boolean actorIsAdmin = PermissionCatalog.isPlatformOrLegacyAdmin(actorRoles);
        if (!actorIsAdmin && ("SUPER_ADMIN".equals(targetRole) || "ADMIN".equals(targetRole) || "ADMIN_EMPRESA".equals(targetRole))) {
            throw new LastAdminProtectionException("Operación denegada: un rol gerencial no puede crear ni asignar cuentas administrativas privilegiadas.");
        }
    }

    private void ensureActorCanModifyTargetAccount(String actorEmail, Account target) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return;
        }
        Account actor = accountRepository.findByEmailIgnoreCase(actorEmail.trim()).orElse(null);
        if (actor == null) {
            return;
        }
        boolean actorIsAdmin = PermissionCatalog.isPlatformOrLegacyAdmin(actor.getRoles());
        boolean targetIsAdmin = PermissionCatalog.isPlatformOrLegacyAdmin(target.getRoles());
        boolean targetIsSuper = PermissionCatalog.isSuperAdmin(target.getRoles());
        if (targetIsSuper && !PermissionCatalog.isSuperAdmin(actor.getRoles())) {
            throw new LastAdminProtectionException("Operación denegada: una cuenta SUPER_ADMIN solo puede gestionarse por plataforma autorizada.");
        }
        if (targetIsAdmin && !actorIsAdmin) {
            throw new LastAdminProtectionException("Operación denegada: no tienes autorización para modificar ni desactivar administradores.");
        }
    }

    private boolean isStaff(Account account) {
        return account.getRoles().stream().anyMatch(PermissionCatalog::isStaffRole);
    }

    private Account requireStaff(UUID id) {
        return accountRepository.findById(id).filter(this::isStaff)
                .orElseThrow(() -> new AccountNotFoundException("Colaborador no encontrado con ID: " + id));
    }
}
