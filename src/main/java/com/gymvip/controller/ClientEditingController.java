package com.gymvip.controller;

import com.gymvip.dto.request.UpdateClientRequest;
import com.gymvip.dto.response.*;
import com.gymvip.service.ClientEditingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientEditingController {
    private final ClientEditingService service;
    public ClientEditingController(ClientEditingService service) { this.service=service; }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public ClientDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest request, Authentication actor) {
        return service.update(id,request,actor.getName());
    }
    @GetMapping("/{id}/changes") @PreAuthorize("hasRole('ADMIN')")
    public Page<ClientChangeResponse> history(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,
                                             @RequestParam(defaultValue="20") int size) { return service.history(id,page,size); }
}
