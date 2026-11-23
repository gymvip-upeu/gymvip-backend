package com.gymvip.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStaffAccountRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El formato de correo no es válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener mínimo 8 caracteres")
        String password,

        @NotBlank(message = "El rol es obligatorio (ADMIN, RECEPCION)")
        String role,
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @Size(max = 30) String phone
) {
    public CreateStaffAccountRequest(String email, String password, String role) {
        this(email, password, role, null, null, null);
    }
    @JsonCreator
    public static CreateStaffAccountRequest fromJson(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("role") String role,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("firstName") String firstName,
            @JsonProperty("lastName") String lastName,
            @JsonProperty("phone") String phone
    ) {
        String resolvedRole = (role != null && !role.isBlank())
                ? role.trim()
                : (roles != null && !roles.isEmpty() && roles.get(0) != null ? roles.get(0).trim() : null);
        return new CreateStaffAccountRequest(email, password, resolvedRole, firstName, lastName, phone);
    }
}
