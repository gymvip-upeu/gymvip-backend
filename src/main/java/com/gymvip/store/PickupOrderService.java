package com.gymvip.store;

import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
class PickupOrderService {
    private final JdbcTemplate jdbc;
    private final MercadoPagoSandbox gateway;
    private final Clock clock;

    PickupOrderService(JdbcTemplate jdbc, MercadoPagoSandbox gateway, Clock clock) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.clock = clock;
    }

    boolean enabled() {
        return gateway.enabled();
    }

    List<OrderView> list(UUID client) {
        return jdbc.query("""
                SELECT * FROM sandbox_pickup_orders WHERE client_id = ?
                ORDER BY created_at DESC, id DESC LIMIT 25
                """, (row, index) -> map(row).view(), client);
    }

    OrderView create(UUID client, UUID request, UUID product, int quantity, BigDecimal expectedPrice) {
        requireEnabled();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));
        jdbc.update("""
                INSERT INTO sandbox_pickup_orders
                    (id, client_id, request_id, supplement_id, product_name, unit_price_pen, quantity)
                SELECT ?, ?, ?, id, name, effective_price, ? FROM (
                    SELECT s.*, s.price_pen - COALESCE((
                        SELECT p.reference_price_pen - p.offer_price_pen
                        FROM supplement_promotions p
                        WHERE p.supplement_id = s.id AND p.enabled AND ? BETWEEN p.start_date AND p.end_date
                    ), 0) AS effective_price
                    FROM supplements s WHERE s.id = ? AND s.published AND s.availability = 'DISPONIBLE'
                ) item WHERE effective_price = ?
                ON CONFLICT (client_id, request_id) DO NOTHING
                """, UUID.randomUUID(), client, request, quantity, today, product, expectedPrice);
        var order = jdbc.query("SELECT * FROM sandbox_pickup_orders WHERE client_id = ? AND request_id = ?",
                (row, index) -> map(row), client, request).stream().findFirst()
                .orElseThrow(() -> new ConflictException("Cambió el precio o la disponibilidad. Actualiza el catálogo."));
        if (!order.product().equals(product) || order.quantity() != quantity ||
                order.unitPrice().compareTo(expectedPrice) != 0) {
            throw new ConflictException("La solicitud ya fue usada para un pedido distinto.");
        }
        return ensureCheckout(client, order);
    }

    OrderView refresh(UUID client, UUID id) {
        requireEnabled();
        var order = find(client, id);
        if (order.providerId() == null) return ensureCheckout(client, order);

        var provider = gateway.get(order.providerId(), id, order.view().totalPen());
        String updated = switch (provider.status() == null ? "" : provider.status()) {
            case "processed" -> checkProcessedPayment(provider, order);
            case "refunded", "charged_back" -> "REEMBOLSADO";
            case "failed" -> "RECHAZADO";
            case "canceled", "cancelled" -> "CANCELADO";
            case "expired" -> "VENCIDO";
            default -> order.status();
        };
        jdbc.update("UPDATE sandbox_pickup_orders SET status = ? WHERE id = ? AND client_id = ? AND status = ?",
                updated, id, client, order.status());
        return find(client, id).view();
    }

    private String checkProcessedPayment(MercadoPagoSandbox.ProviderOrder provider, Order order) {
        if ("partially_refunded".equals(provider.statusDetail())) return "REEMBOLSADO";
        if (!"accredited".equals(provider.statusDetail()) || provider.totalPaid() == null ||
                provider.totalPaid().compareTo(order.view().totalPen()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Mercado Pago no confirmó el monto completo del pedido.");
        }
        return "PAGADO_PRUEBA";
    }

    private OrderView ensureCheckout(UUID client, Order order) {
        if (order.providerId() != null) return order.view();

        var provider = gateway.create(order.id(), order.productName(), order.unitPrice(), order.quantity());
        jdbc.update("""
                UPDATE sandbox_pickup_orders SET provider_order_id = ?, checkout_url = ?
                WHERE client_id = ? AND id = ? AND provider_order_id IS NULL
                """, provider.id(), provider.checkoutUrl(), client, order.id());
        return find(client, order.id()).view();
    }

    private Order find(UUID client, UUID id) {
        return jdbc.query("SELECT * FROM sandbox_pickup_orders WHERE client_id = ? AND id = ?",
                (row, index) -> map(row), client, id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado."));
    }

    private void requireEnabled() {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El pago de prueba aún no está habilitado.");
    }

    private static Order map(ResultSet row) throws SQLException {
        return new Order(row.getObject("id", UUID.class), row.getObject("supplement_id", UUID.class),
                row.getString("product_name"), row.getInt("quantity"), row.getBigDecimal("unit_price_pen"),
                row.getString("status"), row.getString("provider_order_id"), row.getString("checkout_url"));
    }

    record OrderView(UUID id, String productName, int quantity, BigDecimal totalPen, String status,
                     String checkoutUrl) {}

    private record Order(UUID id, UUID product, String productName, int quantity, BigDecimal unitPrice,
                         String status, String providerId, String checkoutUrl) {
        OrderView view() {
            return new OrderView(id, productName, quantity, unitPrice.multiply(BigDecimal.valueOf(quantity)),
                    status, "PENDIENTE".equals(status) ? checkoutUrl : null);
        }
    }
}
