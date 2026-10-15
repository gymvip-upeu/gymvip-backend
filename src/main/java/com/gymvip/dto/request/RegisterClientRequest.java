package com.gymvip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RegisterClientRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        String lastName,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        LocalDate birthDate,

        String countryCode,

        @NotBlank(message = "El tipo de documento es obligatorio (DNI, CARNET_EXTRANJERIA, PASAPORTE)")
        String documentType,

        @NotBlank(message = "El número de documento es obligatorio")
        String documentNumber,

        String phone,

        String email
) {}
