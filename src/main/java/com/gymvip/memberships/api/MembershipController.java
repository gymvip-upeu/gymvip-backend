package com.gymvip.memberships.api;

import com.gymvip.memberships.application.MembershipService;
import com.gymvip.memberships.domain.Membership;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public record AssignMembershipRequest(
            @NotNull(message = "El ID del cliente es obligatorio")
            UUID clientId,
            @NotNull(message = "El ID del plan es obligatorio")
            UUID planId,
            LocalDate startDate,
            @NotNull(message = "El monto en PEN es obligatorio")
            @Positive(message = "El monto debe ser mayor a 0")
            BigDecimal amountPen,
            @NotBlank(message = "El método de pago es obligatorio (EFECTIVO, YAPE, PLIN, TRANSFERENCIA, POS)")
            String paymentMethod,
            String externalReference,
            String notes
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Membership> assignMembership(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AssignMembershipRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Membership membership = membershipService.assignMembershipWithPayment(
                request.clientId(),
                request.planId(),
                request.startDate(),
                request.amountPen(),
                request.paymentMethod(),
                request.externalReference(),
                request.notes(),
                idempotencyKey,
                null,
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(membership);
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Membership>> listByClient(@PathVariable UUID clientId) {
        return ResponseEntity.ok(membershipService.findByClientId(clientId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Membership> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(membershipService.findById(id));
    }
}
