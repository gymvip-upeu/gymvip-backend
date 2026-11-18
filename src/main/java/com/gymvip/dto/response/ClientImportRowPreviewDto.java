package com.gymvip.dto.response;

import java.time.LocalDate;

/**
 * Detalle de fila para vista previa de importación de clientes.
 * Incluye campos enriquecidos para verificación opcional de DNI con ApiPeruDev (HU-06).
 */
public record ClientImportRowPreviewDto(
        int rowNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryCode,
        String documentType,
        String documentNumber,
        String phone,
        String email,
        String status, // VALID, DUPLICATE_IN_FILE, DUPLICATE_IN_DATABASE, INVALID, REVISION_MANUAL
        String message,
        String verifiedName, // Nombres y apellidos oficiales obtenidos del padrón
        String nameDiscrepancy, // Descripción de discrepancia de nombres entre archivo y padrón
        String identityStatus // ENCONTRADO_EN_FUENTE, NO_ENCONTRADO_EN_FUENTE, FORMATO_INVALIDO, DISCREPANCIA_NOMBRES, etc.
) {
    public ClientImportRowPreviewDto(
            int rowNumber,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String countryCode,
            String documentType,
            String documentNumber,
            String phone,
            String email,
            String status,
            String message
    ) {
        this(rowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber,
                phone, email, status, message, null, null, null);
    }
}
