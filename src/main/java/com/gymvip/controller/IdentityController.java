package com.gymvip.controller;

import com.gymvip.dto.request.DniLookupRequest;
import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.service.IdentityLookupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para consultas autoritativas de identidad.
 * Acceso restringido exclusivamente a roles operativos: ADMIN y RECEPCION.
 * Soporta solicitudes POST (body JSON) y GET (query param ?dni=...).
 */
@RestController
@RequestMapping("/api/v1/identity")
@Validated
public class IdentityController {

    private final IdentityLookupService identityLookupService;

    public IdentityController(IdentityLookupService identityLookupService) {
        this.identityLookupService = identityLookupService;
    }

    /**
     * Consulta en tiempo real los datos asociados a un DNI peruano (8 dígitos) vía POST.
     */
    @PostMapping("/dni/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
    public ResponseEntity<IdentityLookupResponse> lookupDniPost(@Valid @RequestBody DniLookupRequest request) {
        IdentityLookupResponse response = identityLookupService.lookupDni(request.dni());
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta en tiempo real los datos asociados a un DNI peruano (8 dígitos) vía GET.
     * Permite clientes estándar y consultas directas con query param: /api/v1/identity/dni/lookup?dni=12345678
     */
    @GetMapping("/dni/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
    public ResponseEntity<IdentityLookupResponse> lookupDniGet(
            @RequestParam(name = "dni", required = true)
            @NotBlank(message = "El número de DNI es obligatorio")
            @Pattern(regexp = "^\\d{8}$", message = "El DNI debe constar exactamente de 8 dígitos numéricos")
            String dni
    ) {
        IdentityLookupResponse response = identityLookupService.lookupDni(dni);
        return ResponseEntity.ok(response);
    }
}
