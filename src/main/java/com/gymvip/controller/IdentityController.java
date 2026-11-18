package com.gymvip.controller;

import com.gymvip.dto.request.DniLookupRequest;
import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.service.IdentityLookupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para consultas autoritativas de identidad.
 * Acceso restringido exclusivamente a roles operativos: ADMIN y RECEPCION.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {

    private final IdentityLookupService identityLookupService;

    public IdentityController(IdentityLookupService identityLookupService) {
        this.identityLookupService = identityLookupService;
    }

    /**
     * Consulta en tiempo real los datos asociados a un DNI peruano (8 dígitos).
     */
    @PostMapping("/dni/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public ResponseEntity<IdentityLookupResponse> lookupDni(@Valid @RequestBody DniLookupRequest request) {
        IdentityLookupResponse response = identityLookupService.lookupDni(request.dni());
        return ResponseEntity.ok(response);
    }
}
