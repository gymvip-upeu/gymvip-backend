package com.gymvip.dto.importing;

import java.util.List;
import java.util.Map;

/**
 * Colección de DTOs tipados para el Centro de Importación y Migración Masiva Integral (Fase 5).
 * Soporta análisis de múltiples hojas Excel, dependencias relacionales, dry-run y auditoría.
 */
public class DataImportDtos {

    public record SheetSummary(
            String sheetName,
            String entityType,
            int totalRows,
            int validRows,
            int invalidRows,
            int duplicateRows,
            int newRows,
            int existingRows
    ) {}

    public record RowIssue(
            int rowNumber,
            String severity, // "ERROR", "WARNING"
            String field,
            String message
    ) {}

    public record RowPreview(
            int rowNumber,
            String entityCode,
            String status, // "VALID", "WARNING", "ERROR", "DUPLICATE"
            List<String> issues,
            Map<String, Object> data
    ) {}

    public record DataImportPreviewResponse(
            String filename,
            long fileSizeBytes,
            int totalSheets,
            int totalRecords,
            int validRecords,
            int invalidRecords,
            List<SheetSummary> sheetSummaries,
            Map<String, List<RowPreview>> sampleRowsBySheet,
            List<String> dependencyIssues,
            boolean readyForImport
    ) {}

    public record DataImportResultResponse(
            String importId,
            String status, // "SUCCESS", "PARTIAL", "FAILED"
            String actor,
            String timestamp,
            int totalProcessed,
            int totalInserted,
            int totalUpdated,
            int totalSkipped,
            int totalErrors,
            long durationMs,
            Map<String, Integer> insertedByEntity,
            List<String> executionLogs,
            Long auditEventId
    ) {}

    public record DataImportDecisionDto(
            String sheetName,
            int rowNumber,
            String action, // "INCLUDE", "EXCLUDE", "OVERWRITE"
            Map<String, String> fieldOverrides
    ) {}

    public record ImportHistoryItem(
            String id,
            String timestamp,
            String actor,
            String filename,
            String status,
            int recordsInserted,
            int recordsFailed,
            String summary
    ) {}
}
