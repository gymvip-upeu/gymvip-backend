package com.gymvip.enterprise;

import com.gymvip.entity.Account;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnterpriseOperationsTest {

    private static Account mockAdmin() {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setEmail("admin@vipcenterfit.pe");
        a.setRoles(Set.of("ADMIN"));
        a.setOrganizationId(TenantScopeResolver.DEFAULT_ORG_ID);
        a.setPrimaryBranchId(TenantScopeResolver.DEFAULT_BRANCH_ID);
        return a;
    }

    @Test
    void adjustInventoryRejectsNegativeStockAndLogsMovementOnValidDelta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantScopeResolver scope = mock(TenantScopeResolver.class);
        AuditService audit = mock(AuditService.class);
        Account actor = mockAdmin();
        UUID branchId = TenantScopeResolver.DEFAULT_BRANCH_ID;
        UUID suppId = UUID.randomUUID();

        when(scope.resolveActor(any())).thenReturn(actor);
        when(scope.effectiveBranchId(eq(actor), isNull())).thenReturn(branchId);

        EnterpriseOperationsController ctrl = new EnterpriseOperationsController(jdbc, scope, audit);
        var auth = new UsernamePasswordAuthenticationToken("admin@vipcenterfit.pe", null);

        // Stock actual = 3; intentar restar 5 debe lanzar IllegalArgumentException
        when(jdbc.queryForList(contains("FROM branch_supplement_inventory"), eq(branchId), eq(suppId)))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID(), "stock_available", 3)));

        var badReq = new EnterpriseOperationsController.InventoryAdjustRequest(null, suppId, "SALIDA", -5, "Muestra");
        assertThrows(IllegalArgumentException.class, () -> ctrl.adjustInventory(badReq, auth));

        // Ajuste válido +10 debe resultar en stockAfter = 13 y registrar kardex
        var okReq = new EnterpriseOperationsController.InventoryAdjustRequest(null, suppId, "INGRESO", 10, "Reposición");
        var resp = ctrl.adjustInventory(okReq, auth);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(13, resp.getBody().get("stockAfter"));
        verify(jdbc).update(contains("INSERT INTO inventory_movements"), any(), eq(branchId), eq(suppId), eq("INGRESO"), eq(10), eq(13), eq("Reposición"), eq("admin@vipcenterfit.pe"));
    }

    @Test
    void cashRegisterPreventsDoubleOpenAndComputesExpectedBalanceOnMovementsAndClose() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantScopeResolver scope = mock(TenantScopeResolver.class);
        AuditService audit = mock(AuditService.class);
        Account actor = mockAdmin();
        UUID branchId = TenantScopeResolver.DEFAULT_BRANCH_ID;
        UUID cashId = UUID.randomUUID();

        when(scope.resolveActor(any())).thenReturn(actor);
        when(scope.effectiveBranchId(eq(actor), isNull())).thenReturn(branchId);
        when(scope.effectiveBranchId(eq(actor), eq(branchId))).thenReturn(branchId);
        when(scope.effectiveOrganizationId(eq(actor), isNull())).thenReturn(TenantScopeResolver.DEFAULT_ORG_ID);

        EnterpriseOperationsController ctrl = new EnterpriseOperationsController(jdbc, scope, audit);
        var auth = new UsernamePasswordAuthenticationToken("admin@vipcenterfit.pe", null);

        when(jdbc.queryForObject(contains("FROM cash_registers WHERE branch_id"), eq(Integer.class), eq(branchId)))
                .thenReturn(0);
        var openResp = ctrl.openCashRegister(new EnterpriseOperationsController.OpenCashRequest(null, new BigDecimal("200.00"), "Turno mañana"), auth);
        assertEquals(201, openResp.getStatusCode().value());

        when(jdbc.queryForList(contains("FROM cash_registers WHERE id"), eq(cashId)))
                .thenReturn(List.of(Map.of(
                        "branch_id", branchId,
                        "status", "ABIERTA",
                        "expected_closing_pen", new BigDecimal("200.00")
                )));
        var movResp = ctrl.recordCashMovement(cashId, new EnterpriseOperationsController.CashMovementRequest(
                "INGRESO", "EFECTIVO", new BigDecimal("50.00"), "Venta agua mineral"), auth);
        assertEquals(201, movResp.getStatusCode().value());
        assertEquals(new BigDecimal("250.00"), movResp.getBody().get("expectedClosingPen"));

        var closeResp = ctrl.closeCashRegister(cashId, Map.of("declaredClosingPen", new BigDecimal("250.00")), auth);
        assertEquals(200, closeResp.getStatusCode().value());
        assertEquals("CERRADA", closeResp.getBody().get("status"));
    }

    @Test
    void createSupplierValidatesElevenDigitRucAndLoyaltyRequiresOptIn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantScopeResolver scope = mock(TenantScopeResolver.class);
        AuditService audit = mock(AuditService.class);
        Account actor = mockAdmin();
        when(scope.resolveActor(any())).thenReturn(actor);
        when(scope.effectiveOrganizationId(eq(actor), isNull())).thenReturn(TenantScopeResolver.DEFAULT_ORG_ID);

        EnterpriseOperationsController ctrl = new EnterpriseOperationsController(jdbc, scope, audit);
        var auth = new UsernamePasswordAuthenticationToken("admin@vipcenterfit.pe", null);

        var badSupplier = ctrl.createSupplier(new EnterpriseOperationsController.CreateSupplierRequest(
                "123", "Proveedor X", "SUPLEMENTOS", null, null, null), auth);
        assertEquals(400, badSupplier.getStatusCode().value());

        var okSupplier = ctrl.createSupplier(new EnterpriseOperationsController.CreateSupplierRequest(
                "20601234567", "NutriPeru SAC", "SUPLEMENTOS", "Carlos", "999111222", "ventas@nutri.pe"), auth);
        assertEquals(201, okSupplier.getStatusCode().value());

        UUID clientId = UUID.randomUUID();
        UUID branchId = TenantScopeResolver.DEFAULT_BRANCH_ID;
        UUID orgId = TenantScopeResolver.DEFAULT_ORG_ID;
        when(scope.effectiveBranchId(eq(actor), eq(branchId))).thenReturn(branchId);
        when(scope.effectiveOrganizationId(eq(actor), eq(orgId))).thenReturn(orgId);
        when(jdbc.queryForList(contains("FROM clients c LEFT JOIN branches b"), eq(clientId)))
                .thenReturn(List.of(Map.of("branch_id", branchId, "organization_id", orgId)));

        when(jdbc.queryForList(contains("FROM loyalty_balances"), eq(clientId)))
                .thenReturn(List.of(Map.of("enrolled", false, "points_balance", 0, "lifetime_points", 0)));
        var rejectedPts = ctrl.recordLoyaltyPoints(clientId, Map.of("pointsDelta", 50, "reason", "Bono"), auth);
        assertEquals(400, rejectedPts.getStatusCode().value());

        when(jdbc.queryForList(contains("FROM loyalty_balances"), eq(clientId)))
                .thenReturn(List.of(Map.of("enrolled", true, "points_balance", 480, "lifetime_points", 480)));
        var okPts = ctrl.recordLoyaltyPoints(clientId, Map.of("pointsDelta", 30, "reason", "Asistencia mensual"), auth);
        assertEquals(200, okPts.getStatusCode().value());
        assertEquals(510, okPts.getBody().get("pointsBalance"));
        assertEquals("ORO", okPts.getBody().get("tierLevel"));

        // Cross-tenant asset status mutation must be rejected by TenantScopeResolver
        UUID foreignAssetId = UUID.randomUUID();
        UUID foreignOrgId = UUID.randomUUID();
        UUID foreignBranchId = UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM gym_equipment_assets WHERE id = ?"), eq(foreignAssetId)))
                .thenReturn(List.of(Map.of("organization_id", foreignOrgId, "branch_id", foreignBranchId)));
        when(scope.effectiveOrganizationId(eq(actor), eq(foreignOrgId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Cross-tenant denied"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> ctrl.updateAssetStatus(foreignAssetId, Map.of("operationalStatus", "BAJA"), auth));
    }
}
