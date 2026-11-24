package com.gymvip.service;

import com.gymvip.dto.importing.DataImportDtos.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrato de servicio para el Centro de Importación y Migración Masiva Integral de GYM VIP.
 */
public interface DataImportService {

    /**
     * Descarga la variante de plantilla solicitada (EMPTY, DEMO, EXAMPLE).
     */
    byte[] getTemplate(String templateType);

    /**
     * Inspecciona, analiza y valida en modo Dry Run todas las hojas y dependencias del archivo Excel.
     */
    DataImportPreviewResponse previewImport(MultipartFile file);

    /**
     * Ejecuta la persistencia transaccional de los datos aprobados, ordenados por grafo de dependencias.
     */
    DataImportResultResponse confirmImport(MultipartFile file, String actorEmail, String mode, String decisionsJson);

    /**
     * Consulta el historial reciente de auditoría de importaciones masivas.
     */
    List<ImportHistoryItem> getImportHistory();
}
