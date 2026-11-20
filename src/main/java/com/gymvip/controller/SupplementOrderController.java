package com.gymvip.controller;

import com.gymvip.dto.order.SupplementOrderDtos.*;
import com.gymvip.service.SupplementOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/member-app")
public class SupplementOrderController {

    private final SupplementOrderService orderService;

    public SupplementOrderController(SupplementOrderService orderService) {
        this.orderService = orderService;
    }

    // =========================================================================
    // Carrito de compras
    // =========================================================================

    @GetMapping("/cart")
    public CartSummaryDto getCart(Authentication auth) {
        return orderService.getCart(auth.getName());
    }

    @PutMapping("/cart")
    public CartSummaryDto updateCart(Authentication auth, @Valid @RequestBody UpdateCartItemRequest request) {
        return orderService.updateCartItem(auth.getName(), request);
    }

    @DeleteMapping("/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(Authentication auth) {
        orderService.clearCart(auth.getName());
    }

    // =========================================================================
    // Pedidos del socio
    // =========================================================================

    @PostMapping("/orders/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailDto checkout(Authentication auth, @RequestBody(required = false) CheckoutRequest request) {
        return orderService.checkout(auth.getName(), request);
    }

    @GetMapping("/orders")
    public List<OrderDetailDto> listMyOrders(Authentication auth) {
        return orderService.listOrdersForClient(auth.getName());
    }

    @GetMapping("/orders/{id}")
    public OrderDetailDto getMyOrder(Authentication auth, @PathVariable UUID id) {
        return orderService.getOrderForClient(auth.getName(), id);
    }

    @PostMapping("/orders/{id}/evidence")
    public OrderDetailDto submitEvidence(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentEvidenceSubmitRequest request
    ) {
        return orderService.submitPaymentEvidence(auth.getName(), id, request);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderDetailDto cancelOrder(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        return orderService.cancelOrder(auth.getName(), id, request.reason());
    }

    @GetMapping("/orders/{id}/pickup-ticket")
    public PickupTicketDto getPickupTicket(Authentication auth, @PathVariable UUID id) {
        return orderService.getPickupTicket(auth.getName(), id);
    }
}
