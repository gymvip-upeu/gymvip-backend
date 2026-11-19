package com.gymvip.controller;

import com.gymvip.dto.faq.FaqDtos;
import com.gymvip.service.FaqService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/faqs")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class FaqController {
    private final FaqService service;

    public FaqController(FaqService service) {
        this.service = service;
    }

    @GetMapping
    public List<FaqDtos.Record> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaqDtos.Record create(@Valid @RequestBody FaqDtos.Content request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    public FaqDtos.Record edit(@PathVariable UUID id, @Valid @RequestBody FaqDtos.Update request,
                              Authentication auth) {
        return service.edit(id, request, auth.getName());
    }

    @PostMapping("/{id}/{action:publish|activate|deactivate}")
    @PreAuthorize("hasRole('ADMIN')")
    public FaqDtos.Record transition(@PathVariable UUID id, @PathVariable String action,
                                    @Valid @RequestBody FaqDtos.Transition request, Authentication auth) {
        return service.transition(id, request.revision(), action, auth.getName());
    }
}
