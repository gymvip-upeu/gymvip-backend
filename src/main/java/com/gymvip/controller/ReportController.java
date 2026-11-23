package com.gymvip.controller;

import com.gymvip.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) { this.service = service; }

    @GetMapping
    public ReportService.Snapshot report(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                         @RequestParam(required = false) String metrics) {
        return service.snapshot(from, to, metrics);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                          @RequestParam String metrics, @RequestParam String format) throws IOException {
        var snapshot = service.snapshot(from, to, metrics);
        byte[] bytes;
        MediaType mediaType;
        String extension;
        if ("xlsx".equalsIgnoreCase(format)) {
            bytes = service.xlsx(snapshot);
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            extension = "xlsx";
        } else if ("pdf".equalsIgnoreCase(format)) {
            bytes = service.pdf(snapshot);
            mediaType = MediaType.APPLICATION_PDF;
            extension = "pdf";
        } else throw new IllegalArgumentException("Formato de exportacion no permitido.");
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-gymvip." + extension)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(bytes);
    }
}
