package com.gymvip.controller;

import com.gymvip.dto.supplement.SupplementStockDtos.*;
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

    public SupplementStockController(SupplementStockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public SupplementStockSummaryDto getStockSummary(@PathVariable UUID id) {
        return stockService.getStockSummary(id);
    }

    @GetMapping("/stock/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public List<SupplementStockSummaryDto> listStockAlerts() {
        return stockService.listStockAlerts();
    }

    @PostMapping("/{id}/stock/movement")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto recordMovement(
            @PathVariable UUID id,
            @Valid @RequestBody StockMovementRequest request,
            Authentication auth
    ) {
        return stockService.recordMovement(id, request, auth.getName());
    }

    @GetMapping("/{id}/stock/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public List<StockMovementDto> listMovements(@PathVariable UUID id) {
        return stockService.listMovements(id);
    }
}
