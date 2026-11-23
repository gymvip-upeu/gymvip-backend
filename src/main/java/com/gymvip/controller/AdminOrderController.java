package com.gymvip.controller;

import com.gymvip.dto.order.SupplementOrderDtos.*;
import com.gymvip.service.SupplementOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class AdminOrderController {

    private final SupplementOrderService orderService;

    public AdminOrderController(SupplementOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderDetailDto> listOrders(@RequestParam(required = false) String status) {
        return orderService.listOrdersForAdmin(status);
    }

    @GetMapping("/{id}")
    public OrderDetailDto getOrder(@PathVariable UUID id) {
        return orderService.getOrderById(id);
    }

    @PostMapping("/{id}/approve-payment")
    public OrderDetailDto approvePayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentApprovalRequest request,
            Authentication auth
    ) {
        boolean cashOnly = auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return orderService.approvePayment(id, request, auth.getName(), cashOnly);
    }

    @PostMapping("/{id}/reject-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderDetailDto rejectPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRejectionRequest request,
            Authentication auth
    ) {
        return orderService.rejectPayment(id, request, auth.getName());
    }

    @PostMapping("/{id}/ready-for-pickup")
    public OrderDetailDto markReadyForPickup(@PathVariable UUID id, Authentication auth) {
        return orderService.markReadyForPickup(id, auth.getName());
    }

    @GetMapping("/pickup/{token}")
    public PickupTicketDto inspectPickupToken(@PathVariable String token) {
        return orderService.inspectPickupToken(token);
    }

    @PostMapping("/pickup/{token}/deliver")
    public OrderDetailDto deliverOrder(
            @PathVariable String token,
            @RequestBody(required = false) DeliverOrderRequest request,
            Authentication auth
    ) {
        String notes = (request != null) ? request.verificationNotes() : null;
        return orderService.fulfillOrderDelivery(token, auth.getName(), notes);
    }

    @PostMapping("/cleanup-expired")
    public ResponseEntity<Map<String, Object>> cleanupExpiredReservations() {
        int releasedCount = orderService.releaseExpiredReservations();
        return ResponseEntity.ok(Map.of("releasedCount", releasedCount, "status", "SUCCESS"));
    }
}
