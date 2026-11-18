package com.gymvip.dto.guardian;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public class ClientGuardianDtos {

    public record GuardianDto(
            UUID id,
            UUID clientId,
            String fullName,
            String documentType,
            String documentNumber,
            String relationship,
            String phone,
            String email,
            boolean emergencyContact,
            Instant authorizedAtUtc,
            String consentReference
    ) {}

    public record GuardianCreateRequest(
            @NotBlank(message = "El nombre del apoderado o responsable es obligatorio")
            String fullName,

            @NotBlank(message = "El tipo de documento es obligatorio")
            @Pattern(regexp = "DNI|CE|PASAPORTE", message = "Tipo de documento inválido (DNI, CE o PASAPORTE)")
            String documentType,

            @NotBlank(message = "El número de documento es obligatorio")
            String documentNumber,

            @NotBlank(message = "El parentesco o relación legal es obligatorio")
            String relationship,

            @NotBlank(message = "El teléfono de contacto es obligatorio")
            String phone,

            String email,
            boolean emergencyContact,
            String consentReference
    ) {}
}
