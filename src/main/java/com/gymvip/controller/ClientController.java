package com.gymvip.controller;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientDetailResponse;
import com.gymvip.dto.response.ClientResponse;
import com.gymvip.dto.response.PageResponse;
import com.gymvip.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
public class ClientController {

    private final ClientService clientService;

    private final com.gymvip.security.TenantScopeResolver scopes;
    public ClientController(ClientService clientService, com.gymvip.security.TenantScopeResolver scopes) {
        this.clientService = clientService;
        this.scopes = scopes;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('members.create')")
    public ResponseEntity<ClientResponse> registerClient(
            @Valid @RequestBody RegisterClientRequest request,
            Authentication authentication, @RequestParam(required=false) UUID organizationId, @RequestParam(required=false) UUID branchId
    ) {
        String actor = authentication != null ? authentication.getName() : "recepcion_sistema";
        var scope = scopes.operationalScope(authentication, organizationId, branchId);
        var operator = scopes.resolveActor(authentication);
        UUID assigned = scope.branchId();
        if (assigned == null) {
            if (scopes.isGlobalPlatformRole(operator)) throw new IllegalArgumentException("Selecciona la sede de alta.");
            assigned = scopes.effectiveBranchId(operator, null);
        }
        ClientResponse response = clientService.registerClient(request, actor, assigned);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('members.read') and !hasRole('CLIENTE')")
    public ResponseEntity<PageResponse<ClientResponse>> searchClients(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable, Authentication authentication,
            @RequestParam(required=false) UUID organizationId, @RequestParam(required=false) UUID branchId
    ) {
        return ResponseEntity.ok(PageResponse.from(clientService.searchClients(query, pageable, scopes.operationalScope(authentication, organizationId, branchId))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDetailResponse> getClientById(@PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @GetMapping("/code/{memberCode}")
    public ResponseEntity<ClientDetailResponse> getClientByMemberCode(@PathVariable String memberCode) {
        return ResponseEntity.ok(clientService.getClientByMemberCode(memberCode));
    }
}
