package com.gymvip.controller;

import com.gymvip.dto.importing.DataImportDtos.*;
import com.gymvip.service.DataImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-import")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_EMPRESA')")
public class DataImportController {

    private final DataImportService dataImportService;

    public DataImportController(DataImportService dataImportService) {
        this.dataImportService = dataImportService;
    }

    /**
     * Descarga las plantillas oficiales multi-hoja de GYM VIP (Fase 5 & 6).
     * Tipos: 'empty', 'demo', 'example'.
     */
    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) {
        byte[] bytes = dataImportService.getTemplate(type);
        String filename = switch (type.toLowerCase()) {
            case "demo" -> "GYMVIP_Datos_Demo.xlsx";
            case "example" -> "GYMVIP_Ejemplo_Importacion_Integral.xlsx";
            default -> "GYMVIP_Plantilla_Vacia.xlsx";
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /**
     * Dry Run: Valida, detecta dependencias entre hojas y previsualiza registros sin persistir.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DataImportPreviewResponse> previewImport(@RequestParam("file") MultipartFile file) {
        DataImportPreviewResponse response = dataImportService.previewImport(file);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirmación de importación integral: persiste datos respetando dependencias relacionales y decisiones de fila.
     */
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DataImportResultResponse> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "CREATE_UPDATE") String mode,
            @RequestParam(value = "decisions", required = false) String decisions,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        DataImportResultResponse response = dataImportService.confirmImport(file, actor, mode, decisions);
        return ResponseEntity.ok(response);
    }

    /**
     * Historial de auditoría y trazabilidad de importaciones masivas.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ImportHistoryItem>> getImportHistory() {
        return ResponseEntity.ok(dataImportService.getImportHistory());
    }
}
