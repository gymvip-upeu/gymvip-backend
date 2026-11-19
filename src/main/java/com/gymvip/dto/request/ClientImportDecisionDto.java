package com.gymvip.dto.request;

import java.time.LocalDate;

/**
 * Decisión del operador sobre una fila específica durante la vista previa de importación (HU-06).
 * Permite excluir registros, aplicar ediciones manuales y resolver discrepancias con ApiPeruDev/RENIEC.
 */
public record ClientImportDecisionDto(
        int rowNumber,
        boolean excluded,
        boolean edited,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryCode,
        String documentType,
        String documentNumber,
        String phone,
        String email,
        String discrepancyResolution // USE_SOURCE, KEEP_EXCEL, MANUAL_EDIT, EXCLUDE
) {
    public ClientImportDecisionDto(int rowNumber, boolean excluded) {
        this(rowNumber, excluded, false, null, null, null, null, null, null, null, null, null);
    }
}
