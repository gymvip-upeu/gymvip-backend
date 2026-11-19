package com.gymvip.store;

import com.gymvip.service.MemberWorkspaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/member-app/store")
@PreAuthorize("hasRole('CLIENTE')")
class PickupOrderController {
    private final PickupOrderService orders;
    private final MemberWorkspaceService members;

    PickupOrderController(PickupOrderService orders, MemberWorkspaceService members) {
        this.orders = orders;
        this.members = members;
    }

    @GetMapping("/orders")
    OrderList list(Authentication auth) {
        return new OrderList(orders.enabled(), orders.list(memberId(auth)));
    }

    @PostMapping("/orders")
    PickupOrderService.OrderView create(@Valid @RequestBody CreateOrder request, Authentication auth) {
        return orders.create(memberId(auth), request.requestId(), request.productId(),
                request.quantity(), request.expectedUnitPricePen());
    }

    @PostMapping("/orders/{id}/refresh")
    PickupOrderService.OrderView refresh(@PathVariable UUID id, Authentication auth) {
        return orders.refresh(memberId(auth), id);
    }

    private UUID memberId(Authentication auth) {
        UUID client = members.ownClient(auth.getName());
        if (members.mustChange(auth.getName())) {
            throw new AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        }
        return client;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> paymentFailure(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(Map.of("message", failure.getReason()));
    }

    record CreateOrder(@NotNull UUID requestId, @NotNull UUID productId, @Min(1) @Max(10) int quantity,
                       @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal expectedUnitPricePen) {}

    record OrderList(boolean sandboxEnabled, List<PickupOrderService.OrderView> orders) {}
}
