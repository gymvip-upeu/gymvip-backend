package com.gymvip.clients.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * DTO para la solicitud de registro de cliente (HU-01).
 * Contiene exclusivamente los datos personales del cliente sin casillas legales en el alta de UX.
 */
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
