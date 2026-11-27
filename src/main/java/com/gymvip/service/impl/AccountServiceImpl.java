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
import com.gymvip.security.StaffAccessPolicy;
import com.gymvip.security.TenantScopeResolver;
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
    public List<StaffAccountResponse> listStaffAccounts(String actorEmail) {
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.read");
        return accountRepository.findStaffAccounts().stream()
                .filter(target -> StaffAccessPolicy.canSee(actor, target))
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountResponse getById(UUID id, String actorEmail) {
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.read");
        return accountRepository.findById(id)
                .filter(this::isStaff)
                .filter(target -> StaffAccessPolicy.canSee(actor, target))
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada con ID: " + id));
    }

    @Override
    @Transactional
    public StaffAccountResponse createStaffAccount(CreateStaffAccountRequest request, String actorEmail) {
        accountRepository.lockStaffRoleChanges();
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.create");
        String targetRole = requestedRole(request.role());
        if (!StaffAccessPolicy.canDelegate(actor, targetRole)) throw StaffAccessPolicy.denied();

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe una cuenta de personal con ese correo.");
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

        account.setOrganizationId(StaffAccessPolicy.organization(actor));
        UUID branch = actor.getPrimaryBranchId();
        if (branch == null && TenantScopeResolver.DEFAULT_ORG_ID.equals(account.getOrganizationId())) {
            branch = TenantScopeResolver.DEFAULT_BRANCH_ID;
        }
        if ("BRANCH".equals(PermissionCatalog.resolveRoleDefinition(targetRole).level()) && branch == null) {
            throw new IllegalArgumentException("Selecciona una cuenta administradora con sede operativa asignada.");
        }
        account.setPrimaryBranchId(branch);

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
        return changeRole(id, request, actorEmail, false);
    }

    @Override
    @Transactional
    public StaffAccountResponse updatePrivilegedRole(UUID id, UpdateStaffRoleRequest request, String actorEmail) {
        return changeRole(id, request, actorEmail, true);
    }

    private StaffAccountResponse changeRole(UUID id, UpdateStaffRoleRequest request, String actorEmail, boolean privileged) {
        accountRepository.lockStaffRoleChanges();
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        if (privileged && (!PermissionCatalog.isSuperAdmin(actor.getRoles()) || !actor.isMfaEnabled())) {
            throw StaffAccessPolicy.denied();
        }
        StaffAccessPolicy.requirePermission(actor, "employees.assign_role");
        Account account = requireStaff(id);
        String targetRole = requestedRole(request.role());
        StaffAccessPolicy.requireTarget(actor, account, privileged);
        if (!privileged && !StaffAccessPolicy.canDelegate(actor, targetRole)) throw StaffAccessPolicy.denied();
        if (privileged && account.isMustChangePassword()) throw StaffAccessPolicy.denied();
        protectLastAdministrator(account, targetRole);
        if ("BRANCH".equals(PermissionCatalog.resolveRoleDefinition(targetRole).level()) &&
                account.getPrimaryBranchId() == null &&
                !TenantScopeResolver.DEFAULT_ORG_ID.equals(StaffAccessPolicy.organization(account))) {
            throw new IllegalArgumentException("El colaborador necesita una sede operativa.");
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
        accountRepository.lockStaffRoleChanges();
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.inactivate");
        Account account = requireStaff(id);

        String targetStatus = request.status() != null ? request.status().trim().toUpperCase(Locale.ROOT) : "";
        if (!targetStatus.equals("ACTIVO") && !targetStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido: " + targetStatus + ". Valores permitidos: ACTIVO, INACTIVO");
        }

        if ("INACTIVO".equals(targetStatus) && actorEmail != null && actorEmail.trim().equalsIgnoreCase(account.getEmail())) {
            throw new LastAdminProtectionException("Operación denegada: no puedes autoinactivar ni autoeliminar tu propia cuenta.");
        }

        StaffAccessPolicy.requireTarget(actor, account, false);
        if ("INACTIVO".equals(targetStatus)) protectLastAdministrator(account, "");

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
        accountRepository.lockStaffRoleChanges();
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.create");
        Account account = requireStaff(id);
        StaffAccessPolicy.requireTarget(actor, account, false);

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
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.inactivate");
        Account account = requireStaff(id);
        StaffAccessPolicy.requireTarget(actor, account, false);

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
    public List<AuditEvent> listAccountAuditHistory(UUID accountId, String actorEmail) {
        getById(accountId, actorEmail);
        if (auditEventRepository == null) {
            return List.of();
        }
        return auditEventRepository.findTop50ByEntityNameAndEntityIdOrderByTimestampUtcDesc("Account", accountId.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> listRecentStaffAuditEvents(String actorEmail) {
        Account actor = StaffAccessPolicy.actor(accountRepository, actorEmail);
        StaffAccessPolicy.requirePermission(actor, "employees.read");
        if (auditEventRepository == null) {
            return List.of();
        }
        var visibleIds = listStaffAccounts(actorEmail).stream()
                .map(account -> account.id().toString()).collect(Collectors.toSet());
        return auditEventRepository.findTop100ByEntityNameOrderByTimestampUtcDesc("Account").stream()
                .filter(event -> visibleIds.contains(event.getEntityId())).toList();
    }

    private String requestedRole(String raw) {
        String role = PermissionCatalog.normalizeRole(raw);
        if (!PermissionCatalog.isAssignableStaffRole(role)) {
            throw new IllegalArgumentException("Rol de personal inválido.");
        }
        if (!role.equals(PermissionCatalog.canonicalRole(role))) throw StaffAccessPolicy.denied();
        return role;
    }

    private void protectLastAdministrator(Account account, String replacementRole) {
        if (!"ACTIVO".equalsIgnoreCase(account.getStatus())) return;
        if (PermissionCatalog.isSuperAdmin(account.getRoles()) && !"SUPER_ADMIN".equals(replacementRole)
                && accountRepository.countActiveSuperAdmins() <= 1) {
            throw new LastAdminProtectionException("Se requiere otro administrador activo de plataforma.");
        }
        if (StaffAccessPolicy.isCompanyAdmin(account) && !"ADMIN_EMPRESA".equals(replacementRole)
                && accountRepository.countActiveCompanyAdmins(StaffAccessPolicy.organization(account)) <= 1) {
            throw new LastAdminProtectionException("Se requiere otro administrador activo en la empresa.");
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
