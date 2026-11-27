package com.gymvip.security;

import com.gymvip.entity.Account;
import com.gymvip.enterprise.BranchEntity;
import com.gymvip.enterprise.BranchRepository;
import com.gymvip.repository.AccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resuelve y aplica aislamiento multi-empresa y multi-sede en operaciones de backend (TEC-11).
 */
@Component
public class TenantScopeResolver {

    public static final UUID DEFAULT_ORG_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID DEFAULT_BRANCH_ID = UUID.fromString("22222222-2222-4222-8222-222222222221");

    private final AccountRepository accountRepository;
    private final BranchRepository branchRepository;

    public TenantScopeResolver(AccountRepository accountRepository, BranchRepository branchRepository) {
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
    }

    public Account resolveActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Sesión no autenticada.");
        }
        return accountRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Cuenta operativa no localizada."));
    }

    public boolean isGlobalPlatformRole(Account actor) {
        if (actor == null || actor.getRoles() == null) return false;
        Set<String> roles = actor.getRoles();
        return roles.contains("SUPER_ADMIN");
    }

    public boolean isCompanyWideRole(Account actor) {
        if (isGlobalPlatformRole(actor)) return true;
        if (actor == null || actor.getRoles() == null) return false;
        Set<String> roles = actor.getRoles();
        return roles.contains("ADMIN")
                || roles.contains("ADMIN_EMPRESA")
                || roles.contains("GERENTE_GENERAL")
                || roles.contains("GERENTE")
                || roles.contains("AUDITOR_EMPRESA")
                || roles.contains("CONTADOR")
                || roles.contains("RECURSOS_HUMANOS");
    }

    public UUID effectiveOrganizationId(Account actor, UUID requestedOrgId) {
        UUID actorOrg = actor.getOrganizationId() != null ? actor.getOrganizationId() : DEFAULT_ORG_ID;
        if (isGlobalPlatformRole(actor)) {
            return requestedOrgId != null ? requestedOrgId : actorOrg;
        }
        if (requestedOrgId != null && !Objects.equals(requestedOrgId, actorOrg)) {
            throw new AccessDeniedException("Acceso denegado: no puedes consultar ni modificar datos de otra empresa.");
        }
        return actorOrg;
    }

    public UUID effectiveBranchId(Account actor, UUID requestedBranchId) {
        UUID organizationId = effectiveOrganizationId(actor, null);
        UUID primary = actor.getPrimaryBranchId();
        if (primary == null && actor.getOrganizationId() == null) primary = DEFAULT_BRANCH_ID;

        UUID branch = requestedBranchId != null ? requestedBranchId : primary;
        if (branch == null && isCompanyWideRole(actor)) {
            branch = branchRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                    .filter(candidate -> "ACTIVO".equalsIgnoreCase(candidate.getStatus()))
                    .map(BranchEntity::getId).findFirst().orElse(null);
        }
        if (branch == null || (!isCompanyWideRole(actor) && !Objects.equals(branch, primary))) {
            throw new AccessDeniedException("Acceso denegado: operación restringida a tu sede asignada.");
        }
        BranchEntity assigned = branchRepository.findById(branch)
                .orElseThrow(() -> new AccessDeniedException("Sede operativa no asignada."));
        if (!isGlobalPlatformRole(actor) && !organizationId.equals(assigned.getOrganizationId())) {
            throw new AccessDeniedException("Acceso denegado: la sede pertenece a otra empresa.");
        }
        return branch;
    }
}
