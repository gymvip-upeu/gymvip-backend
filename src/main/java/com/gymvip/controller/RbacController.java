package com.gymvip.controller;

import com.gymvip.enterprise.*;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.PermissionCatalog;
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

    public RbacController(AccountRepository accountRepository,
                          AccountBranchAssignmentRepository branchAssignmentRepository,
                          UserPreferenceRepository preferenceRepository,
                          BranchRepository branchRepository) {
        this.accountRepository = accountRepository;
        this.branchAssignmentRepository = branchAssignmentRepository;
        this.preferenceRepository = preferenceRepository;
        this.branchRepository = branchRepository;
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
        body.put("roles", roles);
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

    @GetMapping("/accounts/{accountId}/branches")
    @PreAuthorize("hasAnyAuthority('employees.read','employees.assign_role')")
    public ResponseEntity<List<AccountBranchAssignmentEntity>> getAccountBranches(@PathVariable UUID accountId) {
        return ResponseEntity.ok(branchAssignmentRepository.findByAccountIdAndStatusIgnoreCase(accountId, "ACTIVO"));
    }

    public record BranchAssignmentRequest(UUID branchId, String branchRole, boolean primary) {}

    @PutMapping("/accounts/{accountId}/branches")
    @PreAuthorize("hasAuthority('employees.assign_role')")
    public ResponseEntity<?> assignAccountBranches(
            @PathVariable UUID accountId,
            @RequestBody List<BranchAssignmentRequest> assignments,
            Authentication authentication
    ) {
        Account target = accountRepository.findById(accountId).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Cuenta no encontrada"));
        }
        branchAssignmentRepository.deleteByAccountId(accountId);
        List<AccountBranchAssignmentEntity> saved = new ArrayList<>();
        if (assignments != null) {
            for (BranchAssignmentRequest req : assignments) {
                if (req.branchId() == null) continue;
                BranchEntity branch = branchRepository.findById(req.branchId()).orElse(null);
                if (branch == null) continue;
                String role = PermissionCatalog.normalizeRole(req.branchRole());
                if (!PermissionCatalog.isAssignableStaffRole(role)) continue;
                AccountBranchAssignmentEntity entity = new AccountBranchAssignmentEntity();
                entity.setId(UUID.randomUUID());
                entity.setAccountId(accountId);
                entity.setOrganizationId(branch.getOrganizationId());
                entity.setBranchId(branch.getId());
                entity.setBranchRole(role);
                entity.setPrimary(req.primary());
                entity.setStatus("ACTIVO");
                entity.setAssignedBy(authentication != null ? authentication.getName() : "sistema");
                saved.add(branchAssignmentRepository.save(entity));
                if (req.primary()) {
                    target.setOrganizationId(branch.getOrganizationId());
                    target.setPrimaryBranchId(branch.getId());
                }
            }
        }
        accountRepository.save(target);
        return ResponseEntity.ok(saved);
    }
}
