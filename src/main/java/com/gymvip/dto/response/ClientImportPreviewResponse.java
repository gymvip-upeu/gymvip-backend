package com.gymvip.dto.response;

import java.util.List;

/**
 * Resumen de vista previa de importación masiva.
 * Contiene métricas de validación de archivo y de auditoría de DNI con ApiPeruDev.
 */
public record ClientImportPreviewResponse(
        String fileName,
        int totalRows,
        int validCount,
        int duplicateCount,
        int invalidCount,
        List<ClientImportRowPreviewDto> rows,
        boolean dniVerificationEnabled,
        int dniValidCount,
        int dniQueriesEstimated,
        int nameDiscrepancyCount
) {
    public ClientImportPreviewResponse(
            String fileName,
            int totalRows,
            int validCount,
            int duplicateCount,
            int invalidCount,
            List<ClientImportRowPreviewDto> rows
    ) {
        this(fileName, totalRows, validCount, duplicateCount, invalidCount, rows, false, 0, 0, 0);
    }
}
