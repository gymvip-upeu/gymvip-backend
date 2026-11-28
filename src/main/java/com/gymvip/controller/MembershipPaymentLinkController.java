package com.gymvip.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymvip.service.payment.MembershipPaymentLinkService;
import com.gymvip.service.payment.SupplementPaymentLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
public class MembershipPaymentLinkController {
    private final MembershipPaymentLinkService service;
    private final SupplementPaymentLinkService supplementLinks;
    public MembershipPaymentLinkController(MembershipPaymentLinkService service, SupplementPaymentLinkService supplementLinks) {
        this.service = service;
        this.supplementLinks = supplementLinks;
    }

    @GetMapping("/api/v1/memberships/payment-links/config")
    @PreAuthorize("hasAuthority('payments.read') or hasAnyRole('ADMIN','GERENTE','RECEPCION','SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','CAJERO')")
    public Map<String, Object> config() { return Map.of("culqiEnabled", service.available(), "culqiMode", service.mode()); }

    @PostMapping("/api/v1/memberships/payment-links")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
    public MembershipPaymentLinkService.LinkStatus create(@RequestBody MembershipPaymentLinkService.CreateRequest request,
                                                           Authentication auth) {
        return service.create(request, auth.getName());
    }

    @GetMapping("/api/v1/memberships/payment-links/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
    public MembershipPaymentLinkService.LinkStatus status(@PathVariable UUID id) { return service.status(id); }

    @PostMapping("/api/v1/payments/culqi/webhook")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) throws Exception {
        String eventId = body.path("id").asText(null);
        service.processEvent(eventId);
        supplementLinks.processEvent(eventId);
        return ResponseEntity.ok().build();
    }
}
