package com.gymvip.controller;

import com.gymvip.dto.request.*;
import com.gymvip.dto.response.*;
import com.gymvip.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/memberships")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service = service; }
    @GetMapping("/payment-methods") public List<String> methods() { return service.paymentMethods(); }
    @PostMapping("/quote") public MembershipQuoteResponse quote(@Valid @RequestBody MembershipQuoteRequest request) {
        return service.quote(request);
    }
    @PostMapping("/sales") public MembershipSaleResponse sell(@Valid @RequestBody MembershipSaleRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        return service.sell(request, key, auth.getName());
    }
    @GetMapping("/clients/{clientId}/history") public List<MembershipSaleResponse> history(@PathVariable UUID clientId) {
        return service.history(clientId);
    }
}
