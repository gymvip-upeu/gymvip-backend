package com.gymvip.service;

import com.gymvip.dto.response.ClientImportPreviewResponse;
import com.gymvip.dto.response.ClientImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ClientImportService {

    /**
     * Genera la plantilla oficial .xlsx para importación masiva de clientes.
     */
    byte[] generateTemplate();

    /**
     * Analiza y valida el archivo .xlsx de clientes sin persistir en la base de datos (Dry Run).
     * Detecta duplicados internos en el archivo y duplicados contra la base de datos.
     */
    ClientImportPreviewResponse previewImport(MultipartFile file);

    /**
     * Confirma la importación: persiste únicamente las filas válidas, genera códigos de socio únicos,
     * omite duplicados sin sobrescribir clientes existentes y registra evento de auditoría.
     */
    ClientImportResultResponse confirmImport(MultipartFile file, String actorEmail);
}

