package com.gymvip.service;

import com.gymvip.controller.AdminOrderController;
import com.gymvip.controller.SupplementOrderController;
import com.gymvip.dto.order.SupplementOrderDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SupplementOrderUnitTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private MemberWorkspaceService workspaceService;
    @Mock private Authentication auth;

    private Clock clock;
    private SupplementOrderService orderService;
    private SupplementOrderController memberController;
    private AdminOrderController adminController;

    private final UUID testAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID testClientId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID testOrderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID testCartId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID testSupplementId = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("America/Lima"));
        orderService = new SupplementOrderService(jdbc, workspaceService, clock);
        memberController = new SupplementOrderController(orderService);
        adminController = new AdminOrderController(orderService);

        when(auth.getName()).thenReturn("socio@gymvip.pe");
    }

    private ResultSet mockOrderRow(String orderStatus, String paymentStatus, String fulfillmentStatus, BigDecimal total, String pickupToken) {
        ResultSet rs = mock(ResultSet.class);
        try {
            when(rs.getObject("id", UUID.class)).thenReturn(testOrderId);
            when(rs.getString("order_number")).thenReturn("ORD-20261120-1234");
            when(rs.getObject("client_id", UUID.class)).thenReturn(testClientId);
            when(rs.getString("first_name")).thenReturn("Carlos");
            when(rs.getString("last_name")).thenReturn("Acosta");
            when(rs.getString("member_code")).thenReturn("VIP-999");
            when(rs.getString("phone")).thenReturn("987654321");
            when(rs.getString("order_status")).thenReturn(orderStatus);
            when(rs.getString("payment_status")).thenReturn(paymentStatus);
            when(rs.getString("fulfillment_status")).thenReturn(fulfillmentStatus);
            when(rs.getBigDecimal("subtotal_pen")).thenReturn(total);
            when(rs.getBigDecimal("discount_pen")).thenReturn(BigDecimal.ZERO);
            when(rs.getBigDecimal("total_pen")).thenReturn(total);
            when(rs.getString("payment_method")).thenReturn("YAPE");
            when(rs.getTimestamp("created_at_utc")).thenReturn(Timestamp.from(clock.instant()));
            when(rs.getBoolean("stock_reserved")).thenReturn(true);
            when(rs.getBoolean("has_pickup_token")).thenReturn(pickupToken != null);
            when(rs.getString("pickup_token")).thenReturn(pickupToken);
            when(rs.getTimestamp("pickup_token_issued_at_utc")).thenReturn(pickupToken != null ? Timestamp.from(clock.instant()) : null);
            when(rs.getTimestamp("pickup_token_consumed_at_utc")).thenReturn("ENTREGADO".equals(fulfillmentStatus) ? Timestamp.from(clock.instant()) : null);
        } catch (SQLException ignored) {}
        return rs;
    }

    @Test
    @DisplayName("1. Checkout falla con stock insuficiente para evitar sobreventa")
    void testCheckoutFailsWithInsufficientStock() {
        when(jdbc.query(contains("accounts WHERE email"), any(RowMapper.class), eq("socio@gymvip.pe")))
                .thenReturn(List.of(testAccountId));
        when(workspaceService.findOwnClient("socio@gymvip.pe")).thenReturn(Optional.of(testClientId));
        when(jdbc.query(contains("member_carts WHERE account_id"), any(RowMapper.class), eq(testAccountId)))
                .thenReturn(List.of(testCartId));

        ResultSet cartRs = mock(ResultSet.class);
        try {
            when(cartRs.getObject("supplement_id", UUID.class)).thenReturn(testSupplementId);
            when(cartRs.getInt("quantity")).thenReturn(5);
        } catch (Exception ignored) {}

        when(jdbc.query(contains("member_cart_items"), any(RowMapper.class), eq(testCartId)))
                .thenReturn(List.of(cartRs));

        Map<String, Object> supplementData = Map.of(
                "name", "Proteína Whey Gold",
                "brand", "Optimum Nutrition",
                "presentation", "2 lb",
                "flavor", "Chocolate",
                "price_pen", new BigDecimal("180.00"),
                "stock", 2,
                "availability", "DISPONIBLE",
                "published", true
        );

        when(jdbc.query(contains("supplements"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of(supplementData));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                orderService.checkout("socio@gymvip.pe", new CheckoutRequest("YAPE", "Nota", "idem-1"))
        );

        assertTrue(ex.getMessage().contains("Stock insuficiente"));
    }

    @Test
    @DisplayName("2. Checkout exitoso reserva stock y limpia el carrito")
    void testCheckoutSuccess() {
        when(jdbc.query(contains("accounts WHERE email"), any(RowMapper.class), eq("socio@gymvip.pe")))
                .thenReturn(List.of(testAccountId));
        when(workspaceService.findOwnClient("socio@gymvip.pe")).thenReturn(Optional.of(testClientId));
        when(jdbc.query(contains("member_carts WHERE account_id"), any(RowMapper.class), eq(testAccountId)))
                .thenReturn(List.of(testCartId));

        ResultSet cartRs = mock(ResultSet.class);
        try {
            when(cartRs.getObject("supplement_id", UUID.class)).thenReturn(testSupplementId);
            when(cartRs.getInt("quantity")).thenReturn(2);
        } catch (Exception ignored) {}

        when(jdbc.query(contains("member_cart_items"), any(RowMapper.class), eq(testCartId)))
                .thenReturn(List.of(cartRs));

        Map<String, Object> supplementData = Map.of(
                "name", "Proteína Whey Gold",
                "brand", "Optimum Nutrition",
                "presentation", "2 lb",
                "flavor", "Chocolate",
                "price_pen", new BigDecimal("180.00"),
                "stock", 10,
                "availability", "DISPONIBLE",
                "published", true
        );

        when(jdbc.query(contains("supplements"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of(supplementData));
        when(jdbc.query(contains("supplement_promotions"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of());

        ResultSet orderRs = mockOrderRow("PENDIENTE_PAGO", "PENDIENTE", "PENDIENTE", new BigDecimal("360.00"), null);
        when(jdbc.query(contains("FROM supplement_orders o"), any(RowMapper.class), any(UUID.class)))
                .thenReturn(List.of(orderRs));

        OrderDetailDto order = orderService.checkout("socio@gymvip.pe", new CheckoutRequest("YAPE", "Por la tarde", "idem-2"));
        assertNotNull(order);
        assertEquals("ORD-20261120-1234", order.orderNumber());
        assertEquals(new BigDecimal("360.00"), order.totalPen());

        verify(jdbc).update(contains("DELETE FROM member_cart_items WHERE cart_id = ?"), eq(testCartId));
        verify(jdbc).update(contains("UPDATE supplements SET stock = ?"), eq(8), eq("DISPONIBLE"), any(Instant.class), eq(testSupplementId));
    }

    @Test
    @DisplayName("3. Anti-reutilización de referencia de pago")
    void testPaymentReferenceCannotBeReused() {
        when(jdbc.query(contains("accounts WHERE email"), any(RowMapper.class), eq("socio@gymvip.pe")))
                .thenReturn(List.of(testAccountId));

        Map<String, Object> orderData = Map.of(
                "order_number", "ORD-20261120-1234",
                "order_status", "PENDIENTE_PAGO",
                "payment_status", "PENDIENTE",
                "total_pen", new BigDecimal("180.00"),
                "stock_released", false,
                "reservation_expires", clock.instant().plusSeconds(1800)
        );

        when(jdbc.query(contains("WHERE id = ? AND account_id = ?"), any(RowMapper.class), eq(testOrderId), eq(testAccountId)))
                .thenReturn(List.of(orderData));

        when(jdbc.query(contains("payment_reference = ? AND id <> ?"), any(RowMapper.class), eq("OPERACION-778899"), eq(testOrderId)))
                .thenReturn(List.of("ORD-20261119-9999"));

        PaymentEvidenceSubmitRequest req = new PaymentEvidenceSubmitRequest("OPERACION-778899", "https://img.com/voucher.jpg", "Pagado por Yape");

        ConflictException ex = assertThrows(ConflictException.class, () ->
                orderService.submitPaymentEvidence("socio@gymvip.pe", testOrderId, req)
        );

        assertTrue(ex.getMessage().contains("ya fue registrada previamente"));
    }

    @Test
    @DisplayName("4. Aprobación de pago con monto insuficiente lanza excepción")
    void testApprovePaymentWithInsufficientAmountFails() {
        Map<String, Object> orderData = Map.of(
                "order_number", "ORD-20261120-1234",
                "order_status", "PAGADO_POR_VERIFICAR",
                "payment_status", "EVIDENCIA_SUBIDA",
                "total_pen", new BigDecimal("250.00"),
                "stock_released", false,
                "client_id", testClientId
        );

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderData));

        PaymentApprovalRequest req = new PaymentApprovalRequest(new BigDecimal("200.00"), "Monto incompleto");

        ConflictException ex = assertThrows(ConflictException.class, () ->
                orderService.approvePayment(testOrderId, req, "admin@gymvip.pe")
        );

        assertTrue(ex.getMessage().contains("menor al total requerido"));
    }

    @Test
    @DisplayName("5. Doble aprobación es idempotente y no genera inconsistencias")
    void testApprovePaymentIsIdempotent() {
        Map<String, Object> orderData = Map.of(
                "order_number", "ORD-20261120-1234",
                "order_status", "CONFIRMADO_PREPARACION",
                "payment_status", "VERIFICADO",
                "total_pen", new BigDecimal("250.00"),
                "stock_released", false,
                "client_id", testClientId
        );

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderData));

        ResultSet orderRs = mockOrderRow("CONFIRMADO_PREPARACION", "VERIFICADO", "EN_PREPARACION", new BigDecimal("250.00"), null);
        when(jdbc.query(contains("FROM supplement_orders o"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderRs));

        PaymentApprovalRequest req = new PaymentApprovalRequest(new BigDecimal("250.00"), "Aprobado");
        OrderDetailDto result = orderService.approvePayment(testOrderId, req, "admin@gymvip.pe");

        assertNotNull(result);
        assertEquals("VERIFICADO", result.paymentStatus());
        verify(jdbc, never()).update(contains("UPDATE supplement_orders SET order_status = 'CONFIRMADO_PREPARACION'"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("6. Rechazo de pago libera stock reservado y actualiza kardex")
    void testRejectPaymentReleasesStock() {
        Map<String, Object> orderData = Map.of(
                "order_number", "ORD-20261120-1234",
                "order_status", "PAGADO_POR_VERIFICAR",
                "payment_status", "EVIDENCIA_SUBIDA",
                "stock_reserved", true,
                "stock_released", false,
                "client_id", testClientId
        );

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderData));

        when(jdbc.query(contains("SELECT supplement_id, quantity FROM supplement_order_items"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(Map.of("supplement_id", testSupplementId, "quantity", 2)));

        when(jdbc.query(contains("SELECT stock FROM supplements WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of(8));

        ResultSet orderRs = mockOrderRow("RECHAZADO", "RECHAZADO", "ANULADO", new BigDecimal("250.00"), null);
        when(jdbc.query(contains("FROM supplement_orders o"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderRs));

        PaymentRejectionRequest req = new PaymentRejectionRequest("Voucher borroso e ilegible");
        OrderDetailDto result = orderService.rejectPayment(testOrderId, req, "admin@gymvip.pe");

        assertEquals("RECHAZADO", result.orderStatus());
        verify(jdbc).update(contains("UPDATE supplements SET stock = ?, availability = 'DISPONIBLE'"), eq(10), any(Instant.class), eq(testSupplementId));
        verify(jdbc).update(contains("INSERT INTO supplement_stock_movements"), any(), eq(testSupplementId), eq(2), eq(8), eq(10), contains("LIBERACION_STOCK"), any(), any());
    }

    @Test
    @DisplayName("7. Generación de ticket QR solo permitida si pago está VERIFICADO y pedido LISTO")
    void testGetPickupTicketFailsIfUnpaid() {
        when(jdbc.query(contains("accounts WHERE email"), any(RowMapper.class), eq("socio@gymvip.pe")))
                .thenReturn(List.of(testAccountId));

        ResultSet orderRs = mockOrderRow("PENDIENTE_PAGO", "PENDIENTE", "PENDIENTE", new BigDecimal("180.00"), null);
        when(jdbc.query(contains("o.id = ? AND o.account_id = ?"), any(RowMapper.class), eq(testOrderId), eq(testAccountId)))
                .thenReturn(List.of(orderRs));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                orderService.getPickupTicket("socio@gymvip.pe", testOrderId)
        );

        assertTrue(ex.getMessage().contains("solo está disponible cuando el pago está verificado"));
    }

    @Test
    @DisplayName("8. Inspeccionar ticket QR no consume ni entrega el pedido")
    void testInspectPickupTokenDoesNotDeliver() {
        ResultSet ticketRs = mockOrderRow("LISTO_PARA_RETIRO", "VERIFICADO", "LISTO", new BigDecimal("180.00"), "rtk_valid_token_123");
        when(jdbc.query(contains("o.pickup_token = ?"), any(RowMapper.class), eq("rtk_valid_token_123")))
                .thenReturn(List.of(ticketRs));

        PickupTicketDto ticket = orderService.inspectPickupToken("rtk_valid_token_123");

        assertNotNull(ticket);
        assertFalse(ticket.isConsumed());
        assertEquals("ORD-20261120-1234", ticket.orderNumber());

        verify(jdbc, never()).update(contains("SET order_status = 'ENTREGADO'"), any(Instant.class), anyString(), any(Instant.class), any(Instant.class), any(UUID.class));
    }

    @Test
    @DisplayName("9. Entrega física atómica consume el ticket QR y bloquea entregas duplicadas")
    void testFulfillDeliveryAtomicallyConsumesTicket() {
        Map<String, Object> orderData = Map.of(
                "id", testOrderId,
                "order_number", "ORD-20261120-1234",
                "order_status", "LISTO_PARA_RETIRO",
                "payment_status", "VERIFICADO",
                "fulfillment_status", "LISTO",
                "consumed_at", false,
                "client_id", testClientId
        );

        when(jdbc.query(contains("pickup_token = ?"), any(RowMapper.class), eq("rtk_valid_token_123")))
                .thenReturn(List.of(orderData));

        ResultSet orderRs = mockOrderRow("ENTREGADO", "VERIFICADO", "ENTREGADO", new BigDecimal("180.00"), "rtk_valid_token_123");
        when(jdbc.query(contains("FROM supplement_orders o"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderRs));

        OrderDetailDto delivered = orderService.fulfillOrderDelivery("rtk_valid_token_123", "recepcion@gymvip.pe", "DNI verificado");

        assertEquals("ENTREGADO", delivered.orderStatus());
        verify(jdbc).update(contains("order_status = 'ENTREGADO', fulfillment_status = 'ENTREGADO'"),
                any(Instant.class), eq("recepcion@gymvip.pe"), any(Instant.class), any(Instant.class), eq(testOrderId));
    }

    @Test
    @DisplayName("10. Intento de reutilizar o consumir ticket QR ya entregado falla con conflicto")
    void testReuseConsumedTicketFails() {
        Map<String, Object> orderData = Map.of(
                "id", testOrderId,
                "order_number", "ORD-20261120-1234",
                "order_status", "ENTREGADO",
                "payment_status", "VERIFICADO",
                "fulfillment_status", "ENTREGADO",
                "consumed_at", true,
                "client_id", testClientId
        );

        when(jdbc.query(contains("pickup_token = ?"), any(RowMapper.class), eq("rtk_already_consumed")))
                .thenReturn(List.of(orderData));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                orderService.fulfillOrderDelivery("rtk_already_consumed", "recepcion@gymvip.pe", null)
        );

        assertTrue(ex.getMessage().contains("ya fue consumido anteriormente"));
    }

    @Test
    @DisplayName("11. Cancelación de pedido por usuario libera stock")
    void testCancelOrderReleasesStock() {
        when(jdbc.query(contains("accounts WHERE email"), any(RowMapper.class), eq("socio@gymvip.pe")))
                .thenReturn(List.of(testAccountId));

        Map<String, Object> orderData = Map.of(
                "order_number", "ORD-20261120-1234",
                "order_status", "PENDIENTE_PAGO",
                "payment_status", "PENDIENTE",
                "stock_reserved", true,
                "stock_released", false
        );

        when(jdbc.query(contains("id = ? AND account_id = ?"), any(RowMapper.class), eq(testOrderId), eq(testAccountId)))
                .thenReturn(List.of(orderData));

        when(jdbc.query(contains("SELECT supplement_id, quantity FROM supplement_order_items"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(Map.of("supplement_id", testSupplementId, "quantity", 1)));

        when(jdbc.query(contains("SELECT stock FROM supplements WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of(5));

        ResultSet orderRs = mockOrderRow("CANCELADO", "PENDIENTE", "ANULADO", new BigDecimal("180.00"), null);
        when(jdbc.query(contains("FROM supplement_orders o"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(orderRs));

        OrderDetailDto cancelled = orderService.cancelOrder("socio@gymvip.pe", testOrderId, "Ya no lo necesito");
        assertEquals("CANCELADO", cancelled.orderStatus());
        verify(jdbc).update(contains("UPDATE supplements SET stock = ?, availability = 'DISPONIBLE'"), eq(6), any(Instant.class), eq(testSupplementId));
    }

    @Test
    @DisplayName("12. Liberación de reservas vencidas (>30 min) devuelve stock a disponibles")
    void testReleaseExpiredReservations() {
        when(jdbc.query(contains("WHERE stock_reserved = true AND stock_released = false"), any(RowMapper.class), any(Instant.class)))
                .thenReturn(List.of(Map.of("id", testOrderId, "order_number", "ORD-20261120-1234")));

        when(jdbc.query(contains("SELECT supplement_id, quantity FROM supplement_order_items"), any(RowMapper.class), eq(testOrderId)))
                .thenReturn(List.of(Map.of("supplement_id", testSupplementId, "quantity", 3)));

        when(jdbc.query(contains("SELECT stock FROM supplements WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(testSupplementId)))
                .thenReturn(List.of(2));

        int released = orderService.releaseExpiredReservations();

        assertEquals(1, released);
        verify(jdbc).update(contains("UPDATE supplements SET stock = ?, availability = 'DISPONIBLE'"), eq(5), any(Instant.class), eq(testSupplementId));
        verify(jdbc).update(contains("order_status = 'EXPIRADO', stock_released = true"), any(Instant.class), eq(testOrderId));
    }
}
