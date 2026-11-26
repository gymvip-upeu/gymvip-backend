package com.gymvip.enterprise;

import com.gymvip.entity.Account;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.util.*;

/**
 * Controlador unificado para los módulos empresariales V32 aislados por empresa/sede:
 * RRHH & Turnos (EMP-03), Inventario por Sede (EMP-06), Activos (EMP-07),
 * Proveedores & Compras (EMP-08), Cajas & Movimientos (EMP-09) y Fidelización (EMP-13).
 */
@RestController
@RequestMapping("/api/v1/enterprise/ops")
public class EnterpriseOperationsController {

    private final JdbcTemplate jdbc;
    private final TenantScopeResolver scope;
    private final AuditService audit;

    public EnterpriseOperationsController(JdbcTemplate jdbc, TenantScopeResolver scope, AuditService audit) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.audit = audit;
    }

    // =========================================================================
    // 1. RECURSOS HUMANOS Y TURNOS (EMP-03)
    // =========================================================================

    @GetMapping("/hr/profiles")
    @PreAuthorize("hasAuthority('employees.read')")
    public List<Map<String, Object>> listStaffProfiles(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT sp.id, sp.account_id, a.email, a.first_name, a.last_name, sp.job_title, sp.document_number, " +
                        "sp.hire_date, sp.shift_pattern, sp.specialty, sp.emergency_contact, sp.is_active, sp.branch_id " +
                        "FROM staff_profiles sp JOIN accounts a ON a.id = sp.account_id " +
                        "WHERE (sp.branch_id = ? OR ? = TRUE) ORDER BY a.email ASC",
                effectiveBranch, scope.isGlobalPlatformRole(actor) && branchId == null
        );
    }

    public record StaffProfileUpsert(UUID accountId, UUID branchId, String jobTitle, String documentNumber,
                                     String hireDate, String shiftPattern, String specialty, String emergencyContact) {}

    @PostMapping("/hr/profiles")
    @PreAuthorize("hasAuthority('employees.create')")
    @Transactional
    public ResponseEntity<Map<String, Object>> upsertStaffProfile(@RequestBody StaffProfileUpsert req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        if (req.accountId() == null || req.jobTitle() == null || req.jobTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cuenta y cargo son obligatorios."));
        }
        int exists = jdbc.queryForObject("SELECT count(*) FROM staff_profiles WHERE account_id = ?", Integer.class, req.accountId());
        Date hDate = (req.hireDate() != null && !req.hireDate().isBlank()) ? Date.valueOf(req.hireDate()) : null;
        if (exists > 0) {
            jdbc.update(
                    "UPDATE staff_profiles SET branch_id = ?, job_title = ?, document_number = ?, hire_date = ?, shift_pattern = ?, specialty = ?, emergency_contact = ?, updated_at = NOW() WHERE account_id = ?",
                    branchId, req.jobTitle().trim(), req.documentNumber(), hDate, req.shiftPattern(), req.specialty(), req.emergencyContact(), req.accountId()
            );
        } else {
            jdbc.update(
                    "INSERT INTO staff_profiles(id, account_id, organization_id, branch_id, job_title, document_number, hire_date, shift_pattern, specialty, emergency_contact) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), req.accountId(), orgId, branchId, req.jobTitle().trim(), req.documentNumber(), hDate, req.shiftPattern(), req.specialty(), req.emergencyContact()
            );
        }
        audit.recordEvent(actor.getEmail(), "HR_PROFILE_UPSERT", "StaffProfile", req.accountId().toString(), req.jobTitle());
        return ResponseEntity.ok(Map.of("saved", true, "accountId", req.accountId()));
    }

    @GetMapping("/hr/shifts")
    @PreAuthorize("hasAuthority('employees.read')")
    public List<Map<String, Object>> listShifts(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT s.id, s.account_id, a.email, s.branch_id, s.shift_date, s.start_time, s.end_time, s.status, s.notes " +
                        "FROM staff_shifts s JOIN accounts a ON a.id = s.account_id WHERE s.branch_id = ? ORDER BY s.shift_date DESC, s.start_time ASC LIMIT 100",
                effectiveBranch
        );
    }

    public record CreateShiftRequest(UUID accountId, UUID branchId, String shiftDate, String startTime, String endTime, String notes) {}

    @PostMapping("/hr/shifts")
    @PreAuthorize("hasAuthority('employees.create')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createShift(@RequestBody CreateShiftRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        if (req.accountId() == null || req.shiftDate() == null || req.startTime() == null || req.endTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cuenta, fecha y horario son obligatorios."));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO staff_shifts(id, account_id, branch_id, shift_date, start_time, end_time, notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, req.accountId(), branchId, Date.valueOf(req.shiftDate()),
                Time.valueOf(req.startTime().length() == 5 ? req.startTime() + ":00" : req.startTime()),
                Time.valueOf(req.endTime().length() == 5 ? req.endTime() + ":00" : req.endTime()),
                req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "PROGRAMADO"));
    }

    // =========================================================================
    // 2. INVENTARIO MULTI-SEDE Y KARDEX (EMP-06)
    // =========================================================================

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('inventory.read')")
    public List<Map<String, Object>> listBranchInventory(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT s.id AS supplement_id, s.name, s.brand, s.category, s.price_pen, " +
                        "COALESCE(bsi.stock_available, 0) AS stock_available, COALESCE(bsi.stock_reserved, 0) AS stock_reserved, " +
                        "COALESCE(bsi.min_stock_threshold, 5) AS min_stock_threshold, ? AS branch_id " +
                        "FROM supplements s LEFT JOIN branch_supplement_inventory bsi ON bsi.supplement_id = s.id AND bsi.branch_id = ? " +
                        "ORDER BY s.name ASC",
                effectiveBranch, effectiveBranch
        );
    }

    public record InventoryAdjustRequest(UUID branchId, UUID supplementId, String movementType, int quantityDelta, String reason) {}

    @PostMapping("/inventory/adjust")
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Transactional
    public ResponseEntity<Map<String, Object>> adjustInventory(@RequestBody InventoryAdjustRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        if (req.supplementId() == null || req.quantityDelta() == 0 || req.reason() == null || req.reason().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Suplemento, cantidad distinta de cero y motivo son obligatorios."));
        }
        return ResponseEntity.ok(applyStockDelta(branchId, req.supplementId(),
                req.movementType() != null ? req.movementType() : "AJUSTE",
                req.quantityDelta(), req.reason().trim(), actor.getEmail()));
    }

    private Map<String, Object> applyStockDelta(UUID branchId, UUID supplementId, String movementType,
                                              int delta, String reason, String actorEmail) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, stock_available FROM branch_supplement_inventory WHERE branch_id = ? AND supplement_id = ? FOR UPDATE",
                branchId, supplementId
        );
        int current = rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("stock_available")).intValue();
        int next = current + delta;
        if (next < 0) {
            throw new IllegalArgumentException("El ajuste dejaría el stock en negativo (" + next + "). Stock actual: " + current);
        }
        if (rows.isEmpty()) {
            jdbc.update(
                    "INSERT INTO branch_supplement_inventory(id, branch_id, supplement_id, stock_available) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), branchId, supplementId, next
            );
        } else {
            jdbc.update(
                    "UPDATE branch_supplement_inventory SET stock_available = ?, updated_at = NOW() WHERE branch_id = ? AND supplement_id = ?",
                    next, branchId, supplementId
            );
        }
        jdbc.update(
                "INSERT INTO inventory_movements(id, branch_id, supplement_id, movement_type, quantity_delta, stock_after, reason, actor_email) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), branchId, supplementId, movementType.toUpperCase(Locale.ROOT), delta, next, reason, actorEmail
        );
        return Map.of("supplementId", supplementId, "branchId", branchId, "stockAfter", next);
    }

    @GetMapping("/inventory/movements")
    @PreAuthorize("hasAuthority('inventory.read')")
    public List<Map<String, Object>> listInventoryMovements(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT m.id, s.name AS supplement_name, m.movement_type, m.quantity_delta, m.stock_after, m.reason, m.actor_email, m.created_at_utc " +
                        "FROM inventory_movements m JOIN supplements s ON s.id = m.supplement_id WHERE m.branch_id = ? ORDER BY m.created_at_utc DESC LIMIT 80",
                effectiveBranch
        );
    }

    // =========================================================================
    // 3. ACTIVOS, MÁQUINAS Y EQUIPOS (EMP-07)
    // =========================================================================

    @GetMapping("/assets")
    @PreAuthorize("hasAuthority('branches.read')")
    public List<Map<String, Object>> listAssets(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT id, asset_code, name, category, brand, serial_number, zone_location, operational_status, last_maintenance_date, next_maintenance_date, notes, branch_id " +
                        "FROM gym_equipment_assets WHERE branch_id = ? ORDER BY name ASC",
                effectiveBranch
        );
    }

    public record CreateAssetRequest(UUID branchId, String assetCode, String name, String category, String brand,
                                     String serialNumber, String zoneLocation, String nextMaintenanceDate, String notes) {}

    @PostMapping("/assets")
    @PreAuthorize("hasAuthority('incidents.manage')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAsset(@RequestBody CreateAssetRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        if (req.assetCode() == null || req.assetCode().isBlank() || req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Código patrimonial y nombre de equipo son obligatorios."));
        }
        UUID id = UUID.randomUUID();
        Date nextMaint = (req.nextMaintenanceDate() != null && !req.nextMaintenanceDate().isBlank()) ? Date.valueOf(req.nextMaintenanceDate()) : null;
        jdbc.update(
                "INSERT INTO gym_equipment_assets(id, organization_id, branch_id, asset_code, name, category, brand, serial_number, zone_location, next_maintenance_date, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, orgId, branchId, req.assetCode().trim().toUpperCase(Locale.ROOT), req.name().trim(),
                req.category() != null ? req.category().trim() : "MUSCULACIÓN", req.brand(), req.serialNumber(), req.zoneLocation(), nextMaint, req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "assetCode", req.assetCode().trim().toUpperCase(Locale.ROOT)));
    }

    @PatchMapping("/assets/{id}/status")
    @PreAuthorize("hasAuthority('incidents.manage')")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAssetStatus(@PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        String nextStatus = String.valueOf(body.getOrDefault("operationalStatus", "OPERATIVO")).toUpperCase(Locale.ROOT);
        if (!Set.of("OPERATIVO", "MANTENIMIENTO", "FUERA_DE_SERVICIO", "BAJA").contains(nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estado de activo inválido."));
        }
        jdbc.update("UPDATE gym_equipment_assets SET operational_status = ?, updated_at = NOW() WHERE id = ?", nextStatus, id);
        audit.recordEvent(actor.getEmail(), "ASSET_STATUS_CHANGED", "EquipmentAsset", id.toString(), nextStatus);
        return ResponseEntity.ok(Map.of("id", id, "operationalStatus", nextStatus));
    }

    // =========================================================================
    // 4. PROVEEDORES Y ÓRDENES DE COMPRA (EMP-08)
    // =========================================================================

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('inventory.read')")
    public List<Map<String, Object>> listSuppliers(Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        return jdbc.queryForList("SELECT id, ruc, business_name, category, contact_name, phone, email, status FROM suppliers WHERE organization_id = ? ORDER BY business_name ASC", orgId);
    }

    public record CreateSupplierRequest(String ruc, String businessName, String category, String contactName, String phone, String email) {}

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSupplier(@RequestBody CreateSupplierRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        if (req.ruc() == null || !req.ruc().trim().matches("^[0-9]{11}$") || req.businessName() == null || req.businessName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "RUC de 11 dígitos numéricos y razón social son obligatorios."));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO suppliers(id, organization_id, ruc, business_name, category, contact_name, phone, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, orgId, req.ruc().trim(), req.businessName().trim(), req.category() != null ? req.category().trim() : "SUPLEMENTOS", req.contactName(), req.phone(), req.email()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "ruc", req.ruc().trim()));
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('inventory.read')")
    public List<Map<String, Object>> listPurchaseOrders(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT po.id, po.order_number, s.business_name AS supplier_name, po.status, po.total_pen, po.notes, po.created_by, po.created_at_utc, po.received_at_utc " +
                        "FROM purchase_orders po JOIN suppliers s ON s.id = po.supplier_id WHERE po.branch_id = ? ORDER BY po.created_at_utc DESC LIMIT 80",
                effectiveBranch
        );
    }

    public record PurchaseOrderItemInput(UUID supplementId, String description, int quantity, BigDecimal unitCostPen) {}
    public record CreatePurchaseOrderRequest(UUID branchId, UUID supplierId, String notes, List<PurchaseOrderItemInput> items) {}

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPurchaseOrder(@RequestBody CreatePurchaseOrderRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        if (req.supplierId() == null || req.items() == null || req.items().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Proveedor e ítems son obligatorios."));
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItemInput item : req.items()) {
            if (item.quantity() <= 0 || item.unitCostPen() == null || item.unitCostPen().signum() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cantidad y costo unitario deben ser positivos."));
            }
            total = total.add(item.unitCostPen().multiply(BigDecimal.valueOf(item.quantity())));
        }
        UUID poId = UUID.randomUUID();
        String orderNumber = "OC-" + String.valueOf(System.currentTimeMillis()).substring(7);
        jdbc.update(
                "INSERT INTO purchase_orders(id, order_number, organization_id, branch_id, supplier_id, status, total_pen, notes, created_by) VALUES (?, ?, ?, ?, ?, 'APROBADA', ?, ?, ?)",
                poId, orderNumber, orgId, branchId, req.supplierId(), total, req.notes(), actor.getEmail()
        );
        for (PurchaseOrderItemInput item : req.items()) {
            BigDecimal sub = item.unitCostPen().multiply(BigDecimal.valueOf(item.quantity()));
            jdbc.update(
                    "INSERT INTO purchase_order_items(id, purchase_order_id, supplement_id, item_description, quantity, unit_cost_pen, subtotal_pen) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), poId, item.supplementId(), item.description() != null ? item.description() : "Insumo", item.quantity(), item.unitCostPen(), sub
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", poId, "orderNumber", orderNumber, "totalPen", total, "status", "APROBADA"));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Transactional
    public ResponseEntity<Map<String, Object>> receivePurchaseOrder(@PathVariable UUID id, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT branch_id, status, order_number FROM purchase_orders WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        String status = String.valueOf(rows.getFirst().get("status"));
        if ("RECIBIDA".equals(status) || "ANULADA".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La orden ya fue procesada (" + status + ")."));
        }
        UUID branchId = scope.effectiveBranchId(actor, (UUID) rows.getFirst().get("branch_id"));
        String orderNum = String.valueOf(rows.getFirst().get("order_number"));
        List<Map<String, Object>> items = jdbc.queryForList("SELECT supplement_id, quantity FROM purchase_order_items WHERE purchase_order_id = ?", id);
        for (Map<String, Object> it : items) {
            UUID suppId = (UUID) it.get("supplement_id");
            int qty = ((Number) it.get("quantity")).intValue();
            if (suppId != null && qty > 0) {
                applyStockDelta(branchId, suppId, "COMPRA", qty, "Recepción " + orderNum, actor.getEmail());
            }
        }
        jdbc.update("UPDATE purchase_orders SET status = 'RECIBIDA', received_by = ?, received_at_utc = NOW() WHERE id = ?", actor.getEmail(), id);
        return ResponseEntity.ok(Map.of("id", id, "status", "RECIBIDA"));
    }

    // =========================================================================
    // 5. CAJAS Y MOVIMIENTOS FINANCIEROS (EMP-09)
    // =========================================================================

    @GetMapping("/cash-registers")
    @PreAuthorize("hasAuthority('payments.read')")
    public List<Map<String, Object>> listCashRegisters(Authentication auth, @RequestParam(required = false) UUID branchId) {
        Account actor = scope.resolveActor(auth);
        UUID effectiveBranch = scope.effectiveBranchId(actor, branchId);
        return jdbc.queryForList(
                "SELECT id, opened_by, closed_by, opening_amount_pen, expected_closing_pen, declared_closing_pen, status, notes, opened_at_utc, closed_at_utc " +
                        "FROM cash_registers WHERE branch_id = ? ORDER BY opened_at_utc DESC LIMIT 40",
                effectiveBranch
        );
    }

    public record OpenCashRequest(UUID branchId, BigDecimal openingAmountPen, String notes) {}

    @PostMapping("/cash-registers/open")
    @PreAuthorize("hasAuthority('payments.create')")
    @Transactional
    public ResponseEntity<Map<String, Object>> openCashRegister(@RequestBody OpenCashRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        UUID branchId = scope.effectiveBranchId(actor, req.branchId());
        UUID orgId = scope.effectiveOrganizationId(actor, null);
        BigDecimal opening = req.openingAmountPen() != null ? req.openingAmountPen() : BigDecimal.ZERO;
        if (opening.signum() < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El monto inicial de caja no puede ser negativo."));
        }
        Integer openCount = jdbc.queryForObject("SELECT count(*) FROM cash_registers WHERE branch_id = ? AND status = 'ABIERTA'", Integer.class, branchId);
        if (openCount != null && openCount > 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe una caja abierta en esta sede. Ciérrela antes de abrir otra."));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO cash_registers(id, organization_id, branch_id, opened_by, opening_amount_pen, expected_closing_pen, status, notes) VALUES (?, ?, ?, ?, ?, ?, 'ABIERTA', ?)",
                id, orgId, branchId, actor.getEmail(), opening, opening, req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "ABIERTA", "openingAmountPen", opening));
    }

    public record CashMovementRequest(String movementType, String paymentMethod, BigDecimal amountPen, String concept) {}

    @PostMapping("/cash-registers/{id}/movements")
    @PreAuthorize("hasAuthority('payments.create')")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordCashMovement(@PathVariable UUID id, @RequestBody CashMovementRequest req, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        List<Map<String, Object>> reg = jdbc.queryForList("SELECT branch_id, status, expected_closing_pen FROM cash_registers WHERE id = ? FOR UPDATE", id);
        if (reg.isEmpty()) return ResponseEntity.notFound().build();
        if (!"ABIERTA".equals(reg.getFirst().get("status"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se pueden registrar movimientos en una caja cerrada."));
        }
        scope.effectiveBranchId(actor, (UUID) reg.getFirst().get("branch_id"));
        if (req.amountPen() == null || req.amountPen().signum() <= 0 || req.concept() == null || req.concept().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Monto positivo y concepto son obligatorios."));
        }
        String type = "EGRESO".equalsIgnoreCase(req.movementType()) ? "EGRESO" : "INGRESO";
        BigDecimal currentExpected = (BigDecimal) reg.getFirst().get("expected_closing_pen");
        BigDecimal nextExpected = "EGRESO".equals(type) ? currentExpected.subtract(req.amountPen()) : currentExpected.add(req.amountPen());
        UUID movId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO cash_movements(id, cash_register_id, branch_id, movement_type, source_module, amount_pen, payment_method, concept, actor_email) VALUES (?, ?, ?, ?, 'CAJA_MANUAL', ?, ?, ?, ?)",
                movId, id, reg.getFirst().get("branch_id"), type, req.amountPen(),
                req.paymentMethod() != null ? req.paymentMethod().toUpperCase(Locale.ROOT) : "EFECTIVO",
                req.concept().trim(), actor.getEmail()
        );
        jdbc.update("UPDATE cash_registers SET expected_closing_pen = ? WHERE id = ?", nextExpected, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", movId, "expectedClosingPen", nextExpected));
    }

    @PostMapping("/cash-registers/{id}/close")
    @PreAuthorize("hasAuthority('payments.create')")
    @Transactional
    public ResponseEntity<Map<String, Object>> closeCashRegister(@PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication auth) {
        Account actor = scope.resolveActor(auth);
        List<Map<String, Object>> reg = jdbc.queryForList("SELECT branch_id, status, expected_closing_pen FROM cash_registers WHERE id = ? FOR UPDATE", id);
        if (reg.isEmpty()) return ResponseEntity.notFound().build();
        if (!"ABIERTA".equals(reg.getFirst().get("status"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "La caja ya se encuentra cerrada."));
        }
        scope.effectiveBranchId(actor, (UUID) reg.getFirst().get("branch_id"));
        BigDecimal declared = body.get("declaredClosingPen") != null
                ? new BigDecimal(String.valueOf(body.get("declaredClosingPen")))
                : (BigDecimal) reg.getFirst().get("expected_closing_pen");
        if (declared.signum() < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El monto de cierre declarado no puede ser negativo."));
        }
        jdbc.update("UPDATE cash_registers SET status = 'CERRADA', closed_by = ?, declared_closing_pen = ?, closed_at_utc = NOW() WHERE id = ?",
                actor.getEmail(), declared, id);
        return ResponseEntity.ok(Map.of("id", id, "status", "CERRADA", "declaredClosingPen", declared));
    }

    // =========================================================================
    // 6. PROGRAMA DE FIDELIZACIÓN / PUNTOS OPT-IN (EMP-13)
    // =========================================================================

    @GetMapping("/loyalty/clients/{clientId}")
    @PreAuthorize("hasAuthority('members.read')")
    public Map<String, Object> getLoyaltyBalance(@PathVariable UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT client_id, enrolled, points_balance, lifetime_points, tier_level FROM loyalty_balances WHERE client_id = ?",
                clientId
        );
        if (rows.isEmpty()) {
            return Map.of("clientId", clientId, "enrolled", false, "pointsBalance", 0, "lifetimePoints", 0, "tierLevel", "BRONCE");
        }
        return rows.getFirst();
    }

    @PostMapping("/loyalty/clients/{clientId}/opt-in")
    @PreAuthorize("hasAuthority('members.update')")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleLoyalty(@PathVariable UUID clientId, @RequestBody Map<String, Boolean> body) {
        boolean enrolled = Boolean.TRUE.equals(body.get("enrolled"));
        int count = jdbc.queryForObject("SELECT count(*) FROM loyalty_balances WHERE client_id = ?", Integer.class, clientId);
        if (count == 0) {
            jdbc.update("INSERT INTO loyalty_balances(client_id, enrolled) VALUES (?, ?)", clientId, enrolled);
        } else {
            jdbc.update("UPDATE loyalty_balances SET enrolled = ?, updated_at_utc = NOW() WHERE client_id = ?", enrolled, clientId);
        }
        return ResponseEntity.ok(Map.of("clientId", clientId, "enrolled", enrolled));
    }

    @PostMapping("/loyalty/clients/{clientId}/points")
    @PreAuthorize("hasAuthority('members.update')")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordLoyaltyPoints(@PathVariable UUID clientId, @RequestBody Map<String, Object> body, Authentication auth) {
        int delta = body.get("pointsDelta") instanceof Number n ? n.intValue() : 0;
        String reason = String.valueOf(body.getOrDefault("reason", "Bonificación operativa"));
        if (delta == 0) return ResponseEntity.badRequest().body(Map.of("message", "La variación de puntos debe ser distinta de 0."));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT enrolled, points_balance, lifetime_points FROM loyalty_balances WHERE client_id = ? FOR UPDATE", clientId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("enrolled"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "El socio no tiene habilitada la fidelización opt-in."));
        }
        int cur = ((Number) rows.getFirst().get("points_balance")).intValue();
        int life = ((Number) rows.getFirst().get("lifetime_points")).intValue();
        int next = cur + delta;
        if (next < 0) return ResponseEntity.badRequest().body(Map.of("message", "Saldo insuficiente de puntos."));
        int nextLife = delta > 0 ? life + delta : life;
        String tier = nextLife >= 2000 ? "BLACK_VIP" : nextLife >= 500 ? "ORO" : "BRONCE";
        jdbc.update("UPDATE loyalty_balances SET points_balance = ?, lifetime_points = ?, tier_level = ?, updated_at_utc = NOW() WHERE client_id = ?",
                next, nextLife, tier, clientId);
        jdbc.update("INSERT INTO loyalty_transactions(id, client_id, points_delta, reason, actor_email) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), clientId, delta, reason, auth.getName());
        return ResponseEntity.ok(Map.of("clientId", clientId, "pointsBalance", next, "tierLevel", tier));
    }
}
