package com.gymvip.controller;

import com.gymvip.dto.response.DashboardMetricsResponse;
import com.gymvip.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION', 'AUDITOR')")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public DashboardMetricsResponse getMetrics(Authentication authentication) {
        boolean includeFinancials = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_AUDITOR".equals(a.getAuthority()));
        return service.getMetrics(includeFinancials);
    }
}
