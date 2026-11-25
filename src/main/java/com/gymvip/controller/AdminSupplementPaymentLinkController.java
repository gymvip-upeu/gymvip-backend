package com.gymvip.controller;

import com.gymvip.service.payment.SupplementPaymentLinkService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
public class AdminSupplementPaymentLinkController {
    private final SupplementPaymentLinkService service;

    public AdminSupplementPaymentLinkController(SupplementPaymentLinkService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}/payment-link")
    public SupplementPaymentLinkService.LinkStatus status(@PathVariable UUID orderId) {
        return service.statusForStaff(orderId);
    }
}
