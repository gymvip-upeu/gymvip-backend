package com.gymvip.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El formato de correo no es válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener mínimo 8 caracteres")
        String password,

        @NotBlank(message = "El rol es obligatorio (ADMIN, RECEPCION)")
        String role
) {}
