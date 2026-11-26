package com.gymvip.security;

import com.gymvip.entity.Account;
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

    public static final UUID DEFAULT_ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_BRANCH_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    private final AccountRepository accountRepository;

    public TenantScopeResolver(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
        return roles.contains("SUPER_ADMIN") || roles.contains("ADMIN") || roles.contains("SOPORTE_PLATAFORMA");
    }

    public boolean isCompanyWideRole(Account actor) {
        if (isGlobalPlatformRole(actor)) return true;
        if (actor == null || actor.getRoles() == null) return false;
        Set<String> roles = actor.getRoles();
        return roles.contains("ADMIN_EMPRESA")
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
        UUID actorBranch = actor.getPrimaryBranchId() != null ? actor.getPrimaryBranchId() : DEFAULT_BRANCH_ID;
        if (isGlobalPlatformRole(actor) || isCompanyWideRole(actor)) {
            return requestedBranchId != null ? requestedBranchId : actorBranch;
        }
        if (requestedBranchId != null && !Objects.equals(requestedBranchId, actorBranch)) {
            throw new AccessDeniedException("Acceso denegado: operación restringida a tu sede asignada.");
        }
        return actorBranch;
    }
}
