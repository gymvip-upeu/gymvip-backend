package com.gymvip.controller;

import com.gymvip.dto.guardian.ClientGuardianDtos.*;
import com.gymvip.service.ClientGuardianService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/guardians")
public class ClientGuardianController {

    private final ClientGuardianService guardianService;

    public ClientGuardianController(ClientGuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
    public List<GuardianDto> listGuardians(@PathVariable UUID clientId) {
        return guardianService.listGuardians(clientId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
    @ResponseStatus(HttpStatus.CREATED)
    public GuardianDto addGuardian(
            @PathVariable UUID clientId,
            @Valid @RequestBody GuardianCreateRequest request,
            Authentication auth
    ) {
        return guardianService.addGuardian(clientId, request, auth.getName());
    }

    @DeleteMapping("/{guardianId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGuardian(
            @PathVariable UUID clientId,
            @PathVariable UUID guardianId,
            Authentication auth
    ) {
        guardianService.removeGuardian(clientId, guardianId, auth.getName());
    }
}
