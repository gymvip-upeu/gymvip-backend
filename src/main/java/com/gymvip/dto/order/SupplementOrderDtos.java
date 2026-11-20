package com.gymvip.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class SupplementOrderDtos {

    // =========================================================================
    // DTOs de Carrito
    // =========================================================================
    public record CartItemDto(
            UUID id,
            UUID supplementId,
            String name,
            String brand,
            String presentation,
            String flavor,
            BigDecimal unitPricePen,
            int quantity,
            BigDecimal subtotalPen,
            int availableStock,
            String imageUrl
    ) {}

    public record CartSummaryDto(
            UUID cartId,
            List<CartItemDto> items,
            BigDecimal totalPricePen,
            int totalQuantity
    ) {}

    public record UpdateCartItemRequest(
            @NotNull(message = "El identificador del suplemento es obligatorio") UUID supplementId,
            @Min(value = 0, message = "La cantidad debe ser mayor o igual a 0") int quantity
    ) {}

    // =========================================================================
    // DTOs de Pedido y Checkout
    // =========================================================================
    public record CheckoutRequest(
            String paymentMethod,
            String customerNotes,
            String idempotencyKey
    ) {}

    public record PaymentEvidenceSubmitRequest(
            @NotBlank(message = "La referencia o número de operación es obligatoria") String paymentReference,
            String paymentEvidenceUrl,
            String paymentNotes
    ) {}

    public record PaymentApprovalRequest(
            @NotNull(message = "El monto verificado es obligatorio")
            @DecimalMin(value = "0.01", message = "El monto debe ser positivo")
            BigDecimal verifiedAmountPen,
            String notes
    ) {}

    public record PaymentRejectionRequest(
            @NotBlank(message = "El motivo de rechazo es obligatorio") String reason
    ) {}

    public record CancelOrderRequest(
            @NotBlank(message = "El motivo de cancelación es obligatorio") String reason
    ) {}

    public record DeliverOrderRequest(
            String verificationNotes
    ) {}

    public record OrderItemDto(
            UUID id,
            UUID supplementId,
            String productName,
            String productBrand,
            String productPresentation,
            String productFlavor,
            BigDecimal unitRegularPricePen,
            BigDecimal unitFinalPricePen,
            String appliedPromotionName,
            int quantity,
            BigDecimal lineTotalPen
    ) {}

    public record OrderDetailDto(
            UUID id,
            String orderNumber,
            UUID clientId,
            String clientName,
            String clientDocumentMasked,
            String clientPhone,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus,
            BigDecimal subtotalPen,
            BigDecimal discountPen,
            BigDecimal totalPen,
            String paymentMethod,
            String paymentReference,
            String paymentEvidenceUrl,
            String paymentEvidenceNotes,
            String paymentEvidenceAtUtc,
            String verifiedBy,
            String verifiedAtUtc,
            BigDecimal verifiedAmountPen,
            String rejectionReason,
            String cancellationReason,
            String reservationExpiresAtUtc,
            boolean stockReserved,
            boolean hasPickupToken,
            String fulfilledBy,
            String fulfilledAtUtc,
            String customerNotes,
            String createdAtUtc,
            List<OrderItemDto> items
    ) {}

    public record PickupTicketDto(
            String orderNumber,
            String pickupToken,
            String clientName,
            String clientDocumentMasked,
            List<OrderItemDto> items,
            BigDecimal totalPen,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus,
            String issuedAtUtc,
            boolean isConsumed,
            String consumedAtUtc,
            String instructions,
            String disclaimer
    ) {}
}
