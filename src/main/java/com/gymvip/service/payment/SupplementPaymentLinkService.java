package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.dto.order.SupplementOrderDtos.CheckoutRequest;
import com.gymvip.service.SupplementOrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SupplementPaymentLinkService {
    public record LinkStatus(UUID id, UUID orderId, BigDecimal amountPen, String status,
                             String url, Instant expiresAt) {}
    private record Pending(UUID id, UUID orderId, BigDecimal amountPen, String status, Instant expiresAt) {}
    private record OrderState(String orderStatus, String paymentStatus, String paymentMethod,
                              BigDecimal totalPen, boolean stockReleased, Instant reserveUntil) {}

    private final JdbcTemplate jdbc;
    private final SupplementOrderService orders;
    private final CulqiClient culqi;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public SupplementPaymentLinkService(JdbcTemplate jdbc, SupplementOrderService orders, CulqiClient culqi,
                                        ObjectMapper mapper, Clock clock, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.culqi = culqi;
        this.mapper = mapper;
        this.clock = clock;
        this.transactions = transactions;
    }

    @Transactional
    public LinkStatus create(UUID orderId, String memberEmail) {
        if (!culqi.configured()) throw new IllegalStateException("Culqi de prueba no está configurado en el backend.");
        var ownedOrder = orders.getOrderForClient(memberEmail, orderId);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, orderId.toString());
        var existing = jdbc.query("SELECT id FROM supplement_payment_links WHERE order_id=?",
                (rs, ignored) -> rs.getObject(1, UUID.class), orderId);
        if (!existing.isEmpty()) return status(existing.getFirst(), memberEmail);
        if (!"TARJETA_PASARELA".equals(ownedOrder.paymentMethod()) ||
                !"PENDIENTE".equals(ownedOrder.paymentStatus()) || !ownedOrder.stockReserved())
            throw new ConflictException("Este pedido no admite un enlace de pago digital.");
        Instant reserveUntil = Instant.parse(ownedOrder.reservationExpiresAtUtc());
        Instant expiresAt = clock.instant().plusSeconds(25 * 60);
        if (expiresAt.isAfter(reserveUntil.minusSeconds(30))) expiresAt = reserveUntil.minusSeconds(30);
        if (!expiresAt.isAfter(clock.instant().plusSeconds(60)))
            throw new ConflictException("La reserva está por vencer. Cree un pedido nuevo.");

        UUID id = UUID.randomUUID();
        JsonNode link = culqi.createLink(id, ownedOrder.totalPen(), expiresAt, "Suplementos GYM VIP");
        String providerId = link == null ? "" : link.path("id").asText();
        String url = link == null ? "" : link.path("url").asText();
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!providerId.startsWith("link_") || !"https".equals(uri.getScheme()) || host == null ||
                !(host.equals("culqi.com") || host.endsWith(".culqi.com")))
            throw new IllegalStateException("Culqi devolvió un enlace inválido.");
        jdbc.update("""
            INSERT INTO supplement_payment_links(id,order_id,amount_pen,provider_link_id,provider_url,status,expires_at_utc)
            VALUES (?,?,?,?,?,'PENDING',?)
        """, id, orderId, ownedOrder.totalPen(), providerId, url, expiresAt);
        return new LinkStatus(id, orderId, ownedOrder.totalPen(), "PENDING", url, expiresAt);
    }

    @Transactional
    public LinkStatus checkoutDigital(String memberEmail, String notes, String idempotencyKey) {
        if (!culqi.configured()) throw new IllegalStateException("Culqi de prueba no está configurado en el backend.");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120)
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria.");
        var order = orders.checkout(memberEmail, new CheckoutRequest("TARJETA_PASARELA", notes, idempotencyKey));
        return create(order.id(), memberEmail);
    }

    public boolean available() { return culqi.configured(); }

    public LinkStatus status(UUID linkId, String memberEmail) {
        LinkStatus result = readStatus(linkId);
        orders.getOrderForClient(memberEmail, result.orderId());
        return result;
    }

    public LinkStatus statusForStaff(UUID orderId) {
        orders.getOrderById(orderId);
        UUID linkId = jdbc.query("SELECT id FROM supplement_payment_links WHERE order_id=?",
                (rs, ignored) -> rs.getObject(1, UUID.class), orderId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Este pedido aún no tiene enlace de pago."));
        return readStatus(linkId);
    }

    private LinkStatus readStatus(UUID linkId) {
        LinkStatus result = jdbc.query("""
            SELECT id,order_id,amount_pen,status,provider_url,expires_at_utc
            FROM supplement_payment_links WHERE id=?
        """, (rs, ignored) -> new LinkStatus(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()), linkId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Enlace no encontrado."));
        if ("PENDING".equals(result.status()) && !result.expiresAt().isAfter(clock.instant())) {
            jdbc.update("UPDATE supplement_payment_links SET status='EXPIRED',updated_at_utc=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", linkId);
            return new LinkStatus(result.id(), result.orderId(), result.amountPen(), "EXPIRED", result.url(), result.expiresAt());
        }
        return result;
    }

    public LinkStatus statusForOrder(UUID orderId, String memberEmail) {
        orders.getOrderForClient(memberEmail, orderId);
        UUID linkId = jdbc.query("SELECT id FROM supplement_payment_links WHERE order_id=?",
                (rs, ignored) -> rs.getObject(1, UUID.class), orderId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Este pedido aún no tiene enlace de pago."));
        return status(linkId, memberEmail);
    }

    /** The webhook is untrusted. Only provider-confirmed charge details can authorize an order. */
    public void processEvent(String eventId) throws Exception {
        if (eventId == null || !eventId.matches("evt_[A-Za-z0-9_]{8,80}"))
            throw new IllegalArgumentException("Identificador de evento inválido.");
        JsonNode event = culqi.event(eventId);
        if (event == null || !eventId.equals(event.path("id").asText())) throw new IllegalArgumentException("Evento Culqi inválido.");
        if (!"charge.creation.succeeded".equals(event.path("type").asText())) return;
        JsonNode data = event.path("data");
        if (data.isTextual()) data = mapper.readTree(data.asText());
        String chargeId = data.path("id").asText();
        if (!chargeId.startsWith("chr_")) throw new IllegalArgumentException("Cargo Culqi inválido.");
        JsonNode charge = culqi.charge(chargeId);
        if (charge == null || !chargeId.equals(charge.path("id").asText())) throw new IllegalArgumentException("Cargo no confirmado por Culqi.");
        if (!"venta_exitosa".equals(charge.path("response_code").asText())) return;
        String providerLinkId = firstNonBlank(charge.path("link_id").asText(""),
                charge.path("metadata").path("link_id").asText(""), data.path("link_id").asText(""));
        String description = charge.path("description").asText("");
        List<Pending> matches = jdbc.query("""
            SELECT id,order_id,amount_pen,status,expires_at_utc FROM supplement_payment_links
            WHERE provider_link_id=? OR ? LIKE '%' || id::text || '%'
        """, (rs, ignored) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getBigDecimal(3), rs.getString(4), rs.getTimestamp(5).toInstant()), providerLinkId, description);
        if (matches.size() != 1) return;
        Pending match = matches.getFirst();
        if (!"PEN".equals(charge.path("currency_code").asText()) ||
                charge.path("amount").asInt(-1) != match.amountPen().movePointRight(2).intValueExact()) return;
        transactions.executeWithoutResult(ignored -> finalizeVerified(match.id(), chargeId));
    }

    public void finalizeVerified(UUID linkId, String chargeId) {
        Pending link = jdbc.query("""
            SELECT id,order_id,amount_pen,status,expires_at_utc FROM supplement_payment_links WHERE id=? FOR UPDATE
        """, (rs, ignored) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getBigDecimal(3), rs.getString(4), rs.getTimestamp(5).toInstant()), linkId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Enlace no encontrado."));
        if ("PAID".equals(link.status())) return;
        if (!"PENDING".equals(link.status()) || !link.expiresAt().isAfter(clock.instant())) {
            markReview(linkId, chargeId);
            return;
        }
        List<OrderState> ordersToCheck = jdbc.query("""
            SELECT order_status,payment_status,payment_method,total_pen,stock_released,reservation_expires_at_utc
            FROM supplement_orders WHERE id=? FOR UPDATE
        """, (rs, ignored) -> new OrderState(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getBoolean(5), rs.getTimestamp(6).toInstant()), link.orderId());
        if (ordersToCheck.isEmpty() || !"PENDIENTE_PAGO".equals(ordersToCheck.getFirst().orderStatus()) ||
                !"PENDIENTE".equals(ordersToCheck.getFirst().paymentStatus()) ||
                !"TARJETA_PASARELA".equals(ordersToCheck.getFirst().paymentMethod()) ||
                ordersToCheck.getFirst().stockReleased() ||
                !ordersToCheck.getFirst().reserveUntil().isAfter(clock.instant()) ||
                ordersToCheck.getFirst().totalPen().compareTo(link.amountPen()) != 0) {
            markReview(linkId, chargeId);
            return;
        }
        orders.confirmCulqiPayment(link.orderId(), link.amountPen(), chargeId);
        jdbc.update("""
            UPDATE supplement_payment_links SET status='PAID',provider_transaction_id=?,updated_at_utc=CURRENT_TIMESTAMP WHERE id=?
        """, chargeId, linkId);
    }

    private void markReview(UUID id, String chargeId) {
        jdbc.update("""
            UPDATE supplement_payment_links SET status='REVIEW',provider_transaction_id=?,updated_at_utc=CURRENT_TIMESTAMP WHERE id=?
        """, chargeId, id);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
