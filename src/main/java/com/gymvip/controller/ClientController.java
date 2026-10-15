package com.gymvip.controller;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientDetailResponse;
import com.gymvip.dto.response.ClientResponse;
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
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION', 'SUPERADMIN')")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> registerClient(
            @Valid @RequestBody RegisterClientRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "recepcion_sistema";
        ClientResponse response = clientService.registerClient(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ClientResponse>> searchClients(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(clientService.searchClients(query, pageable));
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
