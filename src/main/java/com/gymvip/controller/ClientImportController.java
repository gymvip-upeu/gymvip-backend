package com.gymvip.controller;

import com.gymvip.dto.response.ClientImportPreviewResponse;
import com.gymvip.dto.response.ClientImportResultResponse;
import com.gymvip.service.ClientImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/clients/import")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_EMPRESA')")
public class ClientImportController {

    private final ClientImportService clientImportService;

    public ClientImportController(ClientImportService clientImportService) {
        this.clientImportService = clientImportService;
    }

    /**
     * Descarga la plantilla oficial .xlsx para importación masiva de clientes.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] excelBytes = clientImportService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_importacion_clientes.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * Vista previa y validación de filas del archivo .xlsx sin persistir.
     * Soporta verificación opcional de DNI con ApiPeruDev mediante query parameter 'verifyDni'.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientImportPreviewResponse> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "verifyDni", defaultValue = "false") boolean verifyDni
    ) {
        ClientImportPreviewResponse response = clientImportService.previewImport(file, verifyDni);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirmación de importación masiva: persiste únicamente las filas válidas y seleccionadas,
     * aplicando decisiones de exclusión, edición y resolución de discrepancias (HU-06).
     */
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientImportResultResponse> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "verifyDni", defaultValue = "false") boolean verifyDni,
            @RequestParam(value = "decisions", required = false) String decisions,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "recepcion_sistema";
        ClientImportResultResponse response = clientImportService.confirmImport(file, actor, verifyDni, decisions);
        return ResponseEntity.ok(response);
    }
}
