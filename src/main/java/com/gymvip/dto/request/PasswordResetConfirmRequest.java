package com.gymvip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "El token de recuperación es obligatorio.")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria.")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres.")
        String newPassword
) {}
