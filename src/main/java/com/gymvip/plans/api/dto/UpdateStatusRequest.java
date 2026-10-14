package com.gymvip.plans.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
        @NotBlank(message = "El estado es requerido (ACTIVO, INACTIVO)")
        String status
) {}
