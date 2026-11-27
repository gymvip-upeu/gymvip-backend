package com.gymvip.controller;

import com.gymvip.enterprise.*;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.PermissionCatalog;
import com.gymvip.security.StaffAccessPolicy;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/rbac")
public class RbacController {

    private final AccountRepository accountRepository;
    private final AccountBranchAssignmentRepository branchAssignmentRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final BranchRepository branchRepository;

    private final SessionRepository sessions;
    private final AuditService audit;

    @Autowired
    public RbacController(AccountRepository accountRepository,
                          AccountBranchAssignmentRepository branchAssignmentRepository,
                          UserPreferenceRepository preferenceRepository,
                          BranchRepository branchRepository, SessionRepository sessions, AuditService audit) {
        this.accountRepository = accountRepository;
        this.branchAssignmentRepository = branchAssignmentRepository;
        this.preferenceRepository = preferenceRepository;
        this.branchRepository = branchRepository;
        this.sessions = sessions;
        this.audit = audit;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentAccessContext(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        Account account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) {
            return ResponseEntity.status(404).build();
        }
        Set<String> roles = account.getRoles() != null ? account.getRoles() : Set.of();
        Set<String> permissions = PermissionCatalog.resolvePermissions(roles);
        List<AccountBranchAssignmentEntity> branches = branchAssignmentRepository
                .findByAccountIdAndStatusIgnoreCase(account.getId(), "ACTIVO");
        UserPreferenceEntity pref = preferenceRepository.findById(account.getId()).orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", account.getId());
        body.put("email", account.getEmail());
        body.put("roles", roles.stream().map(PermissionCatalog::canonicalRole).distinct().toList());
        body.put("permissions", permissions);
        body.put("organizationId", account.getOrganizationId());
        body.put("primaryBranchId", account.getPrimaryBranchId());
        body.put("branchAssignments", branches);
        body.put("mfaEnabled", account.isMfaEnabled());
        body.put("preferences", Map.of(
                "showGuides", pref == null || pref.isShowGuides(),
                "experienceMode", pref != null ? pref.getExperienceMode() : "GUIDED",
                "preferredBranchId", pref != null && pref.getPreferredBranchId() != null ? pref.getPreferredBranchId().toString() : ""
        ));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL','GERENTE_SEDE','AUDITOR','AUDITOR_EMPRESA','SOPORTE_PLATAFORMA')")
    public ResponseEntity<List<PermissionCatalog.RoleDefinition>> listRoles() {
        return ResponseEntity.ok(PermissionCatalog.allRoleDefinitions());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL','AUDITOR','AUDITOR_EMPRESA')")
    public ResponseEntity<List<PermissionCatalog.PermissionDefinition>> listPermissions() {
        return ResponseEntity.ok(PermissionCatalog.allPermissionDefinitions());
    }

    @GetMapping("/assignable-roles")
    @PreAuthorize("hasAnyAuthority('employees.create','employees.assign_role')")
    public List<PermissionCatalog.RoleDefinition> listAssignableRoles(Authentication authentication) {
        return StaffAccessPolicy.delegable(StaffAccessPolicy.actor(accountRepository, authentication.getName()));
    }

    @GetMapping("/accounts/{accountId}/branches")
    @PreAuthorize("hasAnyAuthority('employees.read','employees.assign_role')")
    public List<AccountBranchAssignmentEntity> getAccountBranches(@PathVariable UUID accountId, Authentication authentication) {
        Account actor = StaffAccessPolicy.actor(accountRepository, authentication.getName());
        Account target = accountRepository.findById(accountId).orElseThrow(StaffAccessPolicy::denied);
        if (!StaffAccessPolicy.canSee(actor, target)) throw StaffAccessPolicy.denied();
        return branchAssignmentRepository.findByAccountIdAndStatusIgnoreCase(accountId, "ACTIVO");
    }

    public record BranchAssignmentRequest(UUID branchId, String branchRole, boolean primary) {}

    @PutMapping("/accounts/{accountId}/branches")
    @PreAuthorize("hasAuthority('employees.assign_role')")
    @Transactional
    public List<AccountBranchAssignmentEntity> assignAccountBranches(
            @PathVariable UUID accountId, @RequestBody List<BranchAssignmentRequest> assignments,
            Authentication authentication) {
        accountRepository.lockStaffRoleChanges();
        Account actor = StaffAccessPolicy.actor(accountRepository, authentication.getName());
        Account target = accountRepository.findById(accountId).orElseThrow(StaffAccessPolicy::denied);
        StaffAccessPolicy.requireTarget(actor, target, false);
        if (assignments == null || assignments.isEmpty() ||
                assignments.stream().filter(Objects::nonNull).filter(BranchAssignmentRequest::primary).count() != 1) {
            throw new IllegalArgumentException("Selecciona una sede principal.");
        }
        if (StaffAccessPolicy.hasRole(actor, "GERENTE_SEDE")) throw StaffAccessPolicy.denied();

        Set<UUID> branchIds = new HashSet<>();
        List<AccountBranchAssignmentEntity> validated = new ArrayList<>();
        for (BranchAssignmentRequest request : assignments) {
            if (request == null || request.branchId() == null || !branchIds.add(request.branchId())) {
                throw new IllegalArgumentException("Las sedes deben existir y ser distintas.");
            }
            BranchEntity branch = branchRepository.findById(request.branchId()).orElseThrow(StaffAccessPolicy::denied);
            String code = PermissionCatalog.normalizeRole(request.branchRole());
            if (!"ACTIVO".equalsIgnoreCase(branch.getStatus()) ||
                    !StaffAccessPolicy.organization(target).equals(branch.getOrganizationId()) ||
                    !code.equals(PermissionCatalog.canonicalRole(code)) ||
                    !StaffAccessPolicy.canDelegate(actor, code) || !StaffAccessPolicy.hasRole(target, code)) {
                throw StaffAccessPolicy.denied();
            }
            AccountBranchAssignmentEntity entity = new AccountBranchAssignmentEntity();
            entity.setId(UUID.randomUUID());
            entity.setAccountId(target.getId());
            entity.setOrganizationId(branch.getOrganizationId());
            entity.setBranchId(branch.getId());
            entity.setBranchRole(code);
            entity.setPrimary(request.primary());
            entity.setStatus("ACTIVO");
            entity.setAssignedBy(actor.getEmail());
            validated.add(entity);
        }

        branchAssignmentRepository.deleteByAccountId(accountId);
        branchAssignmentRepository.flush();
        target.setPrimaryBranchId(validated.stream().filter(AccountBranchAssignmentEntity::isPrimary)
                .findFirst().orElseThrow().getBranchId());
        accountRepository.save(target);
        sessions.deleteByAccountId(accountId);
        audit.recordEvent(actor.getEmail(), "UPDATE_STAFF_BRANCHES", "Account", accountId.toString(),
                "Sedes autorizadas actualizadas");
        return branchAssignmentRepository.saveAll(validated);
    }
}
