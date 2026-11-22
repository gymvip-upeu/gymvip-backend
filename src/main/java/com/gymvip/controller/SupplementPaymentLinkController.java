package com.gymvip.controller;

import com.gymvip.service.payment.SupplementPaymentLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/member-app/orders")
@PreAuthorize("hasRole('CLIENTE')")
public class SupplementPaymentLinkController {
    private final SupplementPaymentLinkService service;

    public SupplementPaymentLinkController(SupplementPaymentLinkService service) {
        this.service = service;
    }

    public record DigitalCheckoutRequest(String customerNotes, @NotBlank String idempotencyKey) {}

    @GetMapping("/payment-config")
    public Map<String, Boolean> config() {
        return Map.of("culqiEnabled", service.available());
    }

    @PostMapping("/checkout-culqi")
    public SupplementPaymentLinkService.LinkStatus checkout(@Valid @RequestBody DigitalCheckoutRequest request, Authentication auth) {
        return service.checkoutDigital(auth.getName(), request.customerNotes(), request.idempotencyKey());
    }

    @PostMapping("/{orderId}/payment-link")
    public SupplementPaymentLinkService.LinkStatus create(@PathVariable UUID orderId, Authentication auth) {
        return service.create(orderId, auth.getName());
    }

    @GetMapping("/payment-links/{linkId}")
    public SupplementPaymentLinkService.LinkStatus status(@PathVariable UUID linkId, Authentication auth) {
        return service.status(linkId, auth.getName());
    }

    @GetMapping("/{orderId}/payment-link")
    public SupplementPaymentLinkService.LinkStatus statusForOrder(@PathVariable UUID orderId, Authentication auth) {
        return service.statusForOrder(orderId, auth.getName());
    }
}
