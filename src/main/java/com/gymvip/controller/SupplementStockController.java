package com.gymvip.controller;

import com.gymvip.dto.supplement.SupplementStockDtos.*;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.SupplementStockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplements")
public class SupplementStockController {
    private final SupplementStockService stockService;
    private final TenantScopeResolver scope;

    public SupplementStockController(SupplementStockService stockService, TenantScopeResolver scope) {
        this.stockService = stockService;
        this.scope = scope;
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('supplements.stock.read')")
    public List<SupplementStockSummaryDto> listStock(@RequestParam(required = false) UUID branchId, Authentication auth) {
        return stockService.listBranchStock(scope.effectiveBranchId(scope.resolveActor(auth), branchId), false);
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAuthority('supplements.stock.read')")
    public SupplementStockSummaryDto getStockSummary(@PathVariable UUID id, Authentication auth) {
        checkProduct(id, null, auth);
        return stockService.getStockSummary(id);
    }

    @GetMapping("/stock/alerts")
    @PreAuthorize("hasAuthority('supplements.stock.read')")
    public List<SupplementStockSummaryDto> listStockAlerts(@RequestParam(required = false) UUID branchId, Authentication auth) {
        return stockService.listBranchStock(scope.effectiveBranchId(scope.resolveActor(auth), branchId), true);
    }

    @PostMapping("/{id}/stock/entries")
    @PreAuthorize("hasAuthority('supplements.stock.receive')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto receive(@PathVariable UUID id, @Valid @RequestBody StockEntryRequest request, Authentication auth) {
        checkProduct(id, request.branchId(), auth);
        return stockService.recordEntry(id, request, auth.getName());
    }

    @PostMapping("/{id}/stock/movement")
    @PreAuthorize("hasAuthority('supplements.stock.adjust')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto recordMovement(@PathVariable UUID id, @Valid @RequestBody StockMovementRequest request, Authentication auth) {
        checkProduct(id, null, auth);
        return stockService.recordMovement(id, request, auth.getName());
    }

    @GetMapping("/{id}/stock/movements")
    @PreAuthorize("hasAuthority('supplements.stock.read')")
    public List<StockMovementDto> listMovements(@PathVariable UUID id, Authentication auth) {
        checkProduct(id, null, auth);
        return stockService.listMovements(id);
    }

    private void checkProduct(UUID id, UUID requestedBranch, Authentication auth) {
        UUID productBranch = stockService.productBranch(id);
        scope.effectiveBranchId(scope.resolveActor(auth), requestedBranch != null ? requestedBranch : productBranch);
        if (requestedBranch != null && !requestedBranch.equals(productBranch)) {
            throw new ResourceNotFoundException("El suplemento no está registrado en esa sede.");
        }
    }
}
