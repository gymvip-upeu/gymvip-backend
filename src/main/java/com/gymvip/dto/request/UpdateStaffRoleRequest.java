package com.gymvip.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffRoleRequest(
        @NotBlank(message = "El rol es obligatorio (ADMIN, RECEPCION)")
        String role
) {}
