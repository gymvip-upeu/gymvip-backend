package com.gymvip.controller;

import com.gymvip.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@PreAuthorize("hasAuthority('incidents.read')")
public class IncidentController {
    private final IncidentService service;
    public IncidentController(IncidentService service) { this.service = service; }

    @GetMapping public List<IncidentService.Incident> list(@RequestParam(required = false) String status, Authentication auth) {
        return service.list(status == null || status.isBlank() ? null : status, auth.getName());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('incidents.manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentService.Incident create(@Valid @RequestBody IncidentService.CreateRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('incidents.manage','incidents.close')")
    public IncidentService.Incident close(@PathVariable UUID id,
                                          @Valid @RequestBody IncidentService.CloseRequest request, Authentication auth) {
        return service.close(id, request, auth.getName());
    }
}
