package com.gymvip.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymvip.service.payment.MembershipPaymentLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class MembershipPaymentLinkController {
    private final MembershipPaymentLinkService service;
    public MembershipPaymentLinkController(MembershipPaymentLinkService service) { this.service = service; }

    @PostMapping("/api/v1/memberships/payment-links")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public MembershipPaymentLinkService.LinkStatus create(@RequestBody MembershipPaymentLinkService.CreateRequest request,
                                                           Authentication auth) {
        return service.create(request, auth.getName());
    }

    @GetMapping("/api/v1/memberships/payment-links/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public MembershipPaymentLinkService.LinkStatus status(@PathVariable UUID id) { return service.status(id); }

    @PostMapping("/api/v1/payments/culqi/webhook")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) throws Exception {
        service.processEvent(body.path("id").asText(null));
        return ResponseEntity.ok().build();
    }
}
