package com.gymvip.controller;

import com.gymvip.dto.response.CoverageResponse;
import com.gymvip.dto.response.PageResponse;
import com.gymvip.service.CoverageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coverage")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION','SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','AUDITOR','AUDITOR_EMPRESA')")
public class CoverageController {
    private final CoverageService service;
    private final com.gymvip.security.TenantScopeResolver scopes;
    public CoverageController(CoverageService service, com.gymvip.security.TenantScopeResolver scopes) {
        this.service=service;
        this.scopes=scopes;
    }
    @GetMapping("/clients/{id}")
    public CoverageResponse verify(@PathVariable UUID id) {return service.verify(id);}
    @GetMapping
    @PreAuthorize("(hasAuthority('memberships.read') or hasAnyRole('ADMIN','GERENTE','RECEPCION','SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','GERENTE_SEDE','AUDITOR','AUDITOR_EMPRESA')) and !hasRole('CLIENTE')")
    public PageResponse<CoverageResponse> list(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="ALERTAS") String state,
            @RequestParam(required=false) UUID planId,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            org.springframework.security.core.Authentication auth, @RequestParam(required=false) UUID organizationId, @RequestParam(required=false) UUID branchId) {
        return PageResponse.from(service.list(query,state,planId,from,to,page,size,scopes.operationalScope(auth,organizationId,branchId)));
    }
}
