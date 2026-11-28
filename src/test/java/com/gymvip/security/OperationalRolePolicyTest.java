package com.gymvip.security;

import com.gymvip.entity.Account;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationalRolePolicyTest {
    @Test
    void roleCatalogKeepsLegacyPermissionsButRetiresTheirAssignment() {
        var retired = Set.of("CAJERO", "INVENTARIO", "COMPRAS", "ENTRENADOR_JEFE", "MARKETING",
                "ATENCION_CLIENTE", "MANTENIMIENTO");
        var active = PermissionCatalog.allRoleDefinitions().stream().map(PermissionCatalog.RoleDefinition::code).toList();
        var companyAdmin = account("ADMIN_EMPRESA", UUID.randomUUID());
        for (var role : retired) {
            assertFalse(active.contains(role));
            assertFalse(PermissionCatalog.isAssignableStaffRole(role));
            assertFalse(StaffAccessPolicy.canDelegate(companyAdmin, role));
        }
        assertTrue(PermissionCatalog.hasPermission(Set.of("CAJERO"), "payments.create"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("MARKETING"), "payments.create"));
        assertFalse(StaffAccessPolicy.canDelegate(companyAdmin, "SUPER_ADMIN"));
        assertFalse(StaffAccessPolicy.canDelegate(companyAdmin, "SOPORTE_PLATAFORMA"));
    }

    @Test
    void consolidatedPermissionsGiveOperationalAccessWithoutGrantingElevation() {
        assertTrue(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "payments.create"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "orders.dispatch"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "facilities.update_status"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "communications.drafts"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "inventory.transfer"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "inventory.adjust"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "payments.refund"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("RECEPCION"), "employees.assign_role"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("GERENTE_SEDE"), "inventory.transfer"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("ENTRENADOR"), "training.exercises.publish"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("AUDITOR_EMPRESA"), "incidents.close"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("AUDITOR_EMPRESA"), "payments.create"));
    }

    @Test
    void companyAdminCanReassignRetiredStaffOnlyInsideOwnCompany() {
        var organization = UUID.randomUUID();
        var companyAdmin = account("ADMIN_EMPRESA", organization);
        assertDoesNotThrow(() -> StaffAccessPolicy.requireTarget(companyAdmin, account("CAJERO", organization), false));
        assertThrows(AccessDeniedException.class,
                () -> StaffAccessPolicy.requireTarget(companyAdmin, account("CAJERO", UUID.randomUUID()), false));
        assertThrows(AccessDeniedException.class,
                () -> StaffAccessPolicy.requireTarget(companyAdmin, account("SUPER_ADMIN", organization), false));
    }

    private Account account(String role, UUID organization) {
        var account = new Account();
        account.setId(UUID.randomUUID());
        account.setOrganizationId(organization);
        account.setRoles(Set.of(role));
        return account;
    }
}
