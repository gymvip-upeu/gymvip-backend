package com.gymvip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de consulta autoritativa de identidad por DNI (8 dígitos numéricos).
 */
public record DniLookupRequest(
        @NotBlank(message = "El número de DNI es obligatorio")
        @Pattern(regexp = "^\\d{8}$", message = "El DNI debe constar exactamente de 8 dígitos numéricos")
        String dni
) {}
