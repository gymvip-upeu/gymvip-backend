package com.gymvip.plans.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdatePlanRequest(
        @NotBlank(message = "El nombre del plan no puede estar vacío")
        String name,

        String description,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser de al menos 1 día")
        Integer durationDays,

        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio en PEN debe ser mayor a 0")
        BigDecimal pricePen,

        String benefits
) {}
