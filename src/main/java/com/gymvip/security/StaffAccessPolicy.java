package com.gymvip.security;

import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StaffAccessPolicy {
    private static final Set<String> PLATFORM = Set.of("SUPER_ADMIN", "SOPORTE_PLATAFORMA");
    private static final Set<String> MANAGEMENT = Set.of("GERENTE_GENERAL", "GERENTE_SEDE", "AUDITOR_EMPRESA",
            "CONTADOR", "RECURSOS_HUMANOS");
    private static final Set<String> OPERATIONS = Set.of("RECEPCION", "ENTRENADOR");

    private StaffAccessPolicy() {}

    public static Account actor(AccountRepository accounts, String email) {
        if (email == null || email.isBlank()) throw denied();
        return accounts.findByEmailIgnoreCase(email.trim())
                .filter(a -> "ACTIVO".equalsIgnoreCase(a.getStatus()) && !a.isMustChangePassword())
                .orElseThrow(StaffAccessPolicy::denied);
    }

    public static boolean isCompanyAdmin(Account account) {
        return hasRole(account, "ADMIN_EMPRESA");
    }

    public static boolean hasRole(Account account, String role) {
        return account.getRoles().stream().map(PermissionCatalog::canonicalRole).anyMatch(role::equals);
    }

    public static boolean isPlatform(Account account) {
        return account.getRoles().stream().anyMatch(PLATFORM::contains);
    }

    public static UUID organization(Account account) {
        return account.getOrganizationId() == null ? TenantScopeResolver.DEFAULT_ORG_ID : account.getOrganizationId();
    }

    public static boolean canSee(Account actor, Account target) {
        if (Objects.equals(actor.getId(), target.getId()) || hasRole(actor, "SUPER_ADMIN")) return true;
        if (isPlatform(target) || !organization(actor).equals(organization(target))) return false;
        return !hasRole(actor, "GERENTE_SEDE") || actor.getPrimaryBranchId() != null
                && actor.getPrimaryBranchId().equals(target.getPrimaryBranchId());
    }

    public static boolean canDelegate(Account actor, String code) {
        if (hasRole(actor, "SUPER_ADMIN") || isCompanyAdmin(actor)) {
            return MANAGEMENT.contains(code) || OPERATIONS.contains(code);
        }
        return OPERATIONS.contains(code) && (hasRole(actor, "GERENTE_GENERAL")
                || hasRole(actor, "GERENTE_SEDE") || hasRole(actor, "RECURSOS_HUMANOS"));
    }

    public static List<PermissionCatalog.RoleDefinition> delegable(Account actor) {
        return PermissionCatalog.allRoleDefinitions().stream()
                .filter(role -> canDelegate(actor, role.code())).toList();
    }

    public static void requirePermission(Account actor, String permission) {
        if (!PermissionCatalog.hasPermission(actor.getRoles(), permission)) throw denied();
    }

    public static void requireTarget(Account actor, Account target, boolean privileged) {
        if (Objects.equals(actor.getId(), target.getId()) || !canSee(actor, target)) throw denied();
        if (privileged && hasRole(actor, "SUPER_ADMIN")) return;
        if (isPlatform(target) || isCompanyAdmin(target)
                || target.getRoles().stream().map(PermissionCatalog::canonicalRole)
                        .anyMatch(code -> !canDelegate(actor, code)
                                && !(PermissionCatalog.isRetiredRole(code) && canDelegate(actor, "RECEPCION")))) {
            throw denied();
        }
    }

    public static AccessDeniedException denied() {
        return new AccessDeniedException("La cuenta no tiene permiso para gestionar ese rol o ámbito.");
    }
}
