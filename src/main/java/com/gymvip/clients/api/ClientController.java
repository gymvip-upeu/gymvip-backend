package com.gymvip.clients.api;

import com.gymvip.clients.application.ClientService;
import com.gymvip.clients.domain.Client;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    public record RegisterClientRequest(
            @NotBlank(message = "El nombre es obligatorio")
            String firstName,
            @NotBlank(message = "Los apellidos son obligatorios")
            String lastName,
            @NotNull(message = "La fecha de nacimiento es obligatoria")
            LocalDate birthDate,
            String countryCode,
            @NotBlank(message = "El tipo de documento es obligatorio (DNI, CE, PASAPORTE)")
            String documentType,
            @NotBlank(message = "El número de documento es obligatorio")
            String documentNumber,
            String phone,
            String email,
            boolean termsAccepted,
            boolean privacyAccepted,
            boolean marketingEmailAccepted,
            boolean marketingWhatsAppAccepted
    ) {}

    public record UpdateContactsRequest(
            String phone,
            String email
    ) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Page<Client>> listClients(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<Client> result = clientService.searchClients(query, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Client> getClient(@PathVariable UUID id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Client> registerAdultClient(@Valid @RequestBody RegisterClientRequest request,
                                                      HttpServletRequest httpRequest,
                                                      Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        String ip = httpRequest.getRemoteAddr();

        Client registered = clientService.registerAdultClient(
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.countryCode(),
                request.documentType(),
                request.documentNumber(),
                request.phone(),
                request.email(),
                request.termsAccepted(),
                request.privacyAccepted(),
                request.marketingEmailAccepted(),
                request.marketingWhatsAppAccepted(),
                ip,
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    @PatchMapping("/{id}/contacts")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Client> updateContacts(@PathVariable UUID id,
                                                 @RequestBody UpdateContactsRequest request,
                                                 Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Client updated = clientService.updateContacts(id, request.phone(), request.email(), actor);
        return ResponseEntity.ok(updated);
    }
}
