package com.gymvip.dto.supplement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public class SupplementStockDtos {

    public record StockMovementRequest(
            @NotBlank(message = "El tipo de movimiento es obligatorio (ENTRADA, SALIDA o AJUSTE)")
            @Pattern(regexp = "ENTRADA|SALIDA|AJUSTE", message = "Tipo de movimiento no válido")
            String movementType,

            @Min(value = 1, message = "La cantidad debe ser mayor a cero")
            int quantity,

            @NotBlank(message = "El motivo o justificativo del movimiento es obligatorio")
            String reason
    ) {}

    public record StockEntryRequest(
            @NotNull UUID branchId,
            @NotNull UUID requestId,
            @Min(1) @Max(100000) int quantity,
            @NotBlank @Size(max = 400) String reason
    ) {}

    public record StockMovementDto(
            UUID id,
            UUID supplementId,
            String movementType,
            int quantity,
            int previousStock,
            int newStock,
            String reason,
            String actor,
            Instant createdAtUtc
    ) {}

    public record SupplementStockSummaryDto(
            UUID supplementId,
            String name,
            String brand,
            String presentation,
            int currentStock,
            int minStock,
            boolean isLowStock,
            boolean isOutOfStock,
            String availability
    ) {}
}
