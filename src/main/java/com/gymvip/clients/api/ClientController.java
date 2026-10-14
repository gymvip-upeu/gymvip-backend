package com.gymvip.clients.api;

import com.gymvip.clients.api.dto.ClientDetailResponse;
import com.gymvip.clients.api.dto.ClientResponse;
import com.gymvip.clients.api.dto.RegisterClientRequest;
import com.gymvip.clients.api.mapper.ClientApiMapper;
import com.gymvip.clients.application.port.in.RegisterClientUseCase;
import com.gymvip.clients.application.port.in.SearchClientsUseCase;
import com.gymvip.clients.domain.model.Client;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final RegisterClientUseCase registerClientUseCase;
    private final SearchClientsUseCase searchClientsUseCase;

    public ClientController(RegisterClientUseCase registerClientUseCase,
                            SearchClientsUseCase searchClientsUseCase) {
        this.registerClientUseCase = registerClientUseCase;
        this.searchClientsUseCase = searchClientsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Page<ClientResponse>> listClients(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<Client> domainPage = searchClientsUseCase.searchClients(
                query,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        Page<ClientResponse> responsePage = domainPage.map(ClientApiMapper::toResponse);
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ClientDetailResponse> getClient(@PathVariable UUID id) {
        Client client = searchClientsUseCase.getClientById(id);
        return ResponseEntity.ok(ClientApiMapper.toDetailResponse(client));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ClientResponse> registerAdultClient(
            @Valid @RequestBody RegisterClientRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        String ip = httpRequest.getRemoteAddr();

        RegisterClientUseCase.RegisterClientCommand command = new RegisterClientUseCase.RegisterClientCommand(
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.countryCode(),
                request.documentType(),
                request.documentNumber(),
                request.phone(),
                request.email()
        );

        Client registered = registerClientUseCase.registerClient(command, actor, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientApiMapper.toResponse(registered));
    }
}
