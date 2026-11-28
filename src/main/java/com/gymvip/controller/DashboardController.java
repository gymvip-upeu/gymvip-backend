package com.gymvip.controller;

import com.gymvip.dto.response.DashboardMetricsResponse;
import com.gymvip.service.DashboardService;
import com.gymvip.security.TenantScopeResolver;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyAuthority('members.create','reports.read') or hasAnyRole('ADMIN','GERENTE','RECEPCION','AUDITOR','SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','AUDITOR_EMPRESA')")
public class DashboardController {

    private final DashboardService service;

    private final TenantScopeResolver scopes;

    public DashboardController(DashboardService service, TenantScopeResolver scopes) {
        this.service = service;
        this.scopes = scopes;
    }

    @GetMapping("/metrics")
    public DashboardMetricsResponse getMetrics(Authentication authentication, @RequestParam(required=false) UUID organizationId,
                                               @RequestParam(required=false) UUID branchId) {
        boolean includeFinancials = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "reports.read".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_GERENTE".equals(a.getAuthority())
                        || "ROLE_AUDITOR".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority())
                        || "ROLE_ADMIN_EMPRESA".equals(a.getAuthority())
                        || "ROLE_GERENTE_GENERAL".equals(a.getAuthority())
                        || "ROLE_AUDITOR_EMPRESA".equals(a.getAuthority()));
        return service.getMetrics(includeFinancials, scopes.operationalScope(authentication, organizationId, branchId));
    }
}
