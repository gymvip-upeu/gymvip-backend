package com.gymvip.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlanStatusRequest(
        @NotBlank(message = "El estado es obligatorio (ACTIVO, INACTIVO)")
        String status
) {}
