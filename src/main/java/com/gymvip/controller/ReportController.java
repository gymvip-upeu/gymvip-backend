package com.gymvip.controller;

import com.gymvip.service.ReportService;
import com.gymvip.security.TenantScopeResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','AUDITOR_EMPRESA','CONTADOR','ADMIN','GERENTE','AUDITOR')")
public class ReportController {
    private final ReportService service;
    private final TenantScopeResolver scopeResolver;

    public ReportController(ReportService service, TenantScopeResolver scopeResolver) {
        this.service = service;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping
    public ReportService.Snapshot report(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                         @RequestParam(required = false) String metrics,
                                         @RequestParam(required = false) UUID organizationId,
                                         @RequestParam(required = false) UUID branchId, Authentication authentication) {
        return service.snapshot(from, to, metrics, reportScope(authentication, organizationId, branchId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                          @RequestParam String metrics, @RequestParam String format,
                                          @RequestParam(required = false) UUID organizationId,
                                          @RequestParam(required = false) UUID branchId,
                                          Authentication authentication) throws IOException {
        var snapshot = service.snapshot(from, to, metrics, reportScope(authentication, organizationId, branchId));
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

    private ReportService.Scope reportScope(Authentication authentication, UUID organizationId, UUID branchId) {
        var actor = scopeResolver.resolveActor(authentication);
        UUID organization = scopeResolver.effectiveOrganizationId(actor, organizationId);
        UUID branch = branchId == null && scopeResolver.isCompanyWideRole(actor)
                ? null : scopeResolver.effectiveBranchId(actor, branchId);
        return new ReportService.Scope(organization, branch);
    }
}
