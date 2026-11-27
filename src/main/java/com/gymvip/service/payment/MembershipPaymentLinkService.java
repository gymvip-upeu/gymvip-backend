package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.MembershipQuoteRequest;
import com.gymvip.dto.request.MembershipSaleRequest;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.service.impl.MembershipServiceImpl;
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
public class MembershipPaymentLinkService {
    public record CreateRequest(UUID clientId, UUID planId, MembershipQuoteRequest.Operation operation) {}
    public record LinkStatus(UUID id, UUID clientId, BigDecimal amountPen, String status,
                             String url, Instant expiresAt, UUID salePaymentId) {}
    private record Pending(UUID id, UUID clientId, UUID planId, MembershipQuoteRequest.Operation operation,
                           BigDecimal amountPen, String linkId, String actorEmail, String status, Instant expiresAt) {}

    private final JdbcTemplate jdbc;
    private final MembershipServiceImpl memberships;
    private final CulqiClient culqi;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public boolean available() { return culqi.configured(); }
    public String mode() { return culqi.mode(); }

    public MembershipPaymentLinkService(JdbcTemplate jdbc, MembershipServiceImpl memberships,
                                        CulqiClient culqi, ObjectMapper mapper, Clock clock, TransactionTemplate transactions) {
        this.jdbc = jdbc; this.memberships = memberships; this.culqi = culqi; this.mapper = mapper; this.clock = clock;
        this.transactions = transactions;
    }

    @Transactional
    public LinkStatus create(CreateRequest request, String actorEmail) {
        if (!culqi.configured()) throw new IllegalStateException("Culqi no está configurado en el backend.");
        if (request == null || request.clientId() == null || request.planId() == null || request.operation() == null)
            throw new IllegalArgumentException("Cliente, plan y operación son obligatorios.");
        var quote = memberships.quote(new MembershipQuoteRequest(request.clientId(), request.planId(), request.operation()));
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r -> {},
                request.clientId() + ":" + request.planId() + ":" + request.operation());
        var existing = jdbc.query("""
            SELECT id,client_id,amount_pen,status,provider_url,expires_at_utc,sale_payment_id
            FROM membership_payment_links
            WHERE client_id=? AND plan_id=? AND operation=? AND status IN ('PENDING','REVIEW')
            ORDER BY created_at_utc DESC LIMIT 1
        """, (r,n)-> new LinkStatus(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getBigDecimal(3),
                r.getString(4),r.getString(5),r.getTimestamp(6).toInstant(),r.getObject(7,UUID.class)),
                request.clientId(), request.planId(), request.operation().name());
        if (!existing.isEmpty()) {
            if (existing.getFirst().amountPen().compareTo(quote.pricePen()) == 0) return status(existing.getFirst().id());
            throw new IllegalStateException("Existe un enlace pendiente con otro importe. Concilie el cobro antes de generar otro.");
        }
        Integer unresolved = jdbc.queryForObject("""
            SELECT count(*) FROM membership_payment_links
            WHERE client_id=? AND status IN ('PENDING','REVIEW')
        """, Integer.class, request.clientId());
        if (unresolved != null && unresolved > 0)
            throw new IllegalStateException("Existe un enlace pendiente o en revisión para este cliente.");

        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plusSeconds(24 * 3600L);
        JsonNode link = culqi.createLink(id, quote.pricePen(), expires, "Membresía GYM VIP");
        String providerId = link == null ? "" : link.path("id").asText();
        String url = link == null ? "" : link.path("url").asText();
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!providerId.startsWith("link_") || !"https".equals(uri.getScheme()) || host == null ||
                !(host.equals("culqi.com") || host.endsWith(".culqi.com")))
            throw new IllegalStateException("Culqi devolvió un enlace inválido.");
        jdbc.update("""
            INSERT INTO membership_payment_links(id,client_id,plan_id,operation,amount_pen,provider_link_id,provider_url,
                                                 created_by_email,status,expires_at_utc)
            VALUES (?,?,?,?,?,?,?,?,'PENDING',?)
        """, id, request.clientId(), request.planId(), request.operation().name(), quote.pricePen(),
                providerId, url, actorEmail, expires);
        return new LinkStatus(id, request.clientId(), quote.pricePen(), "PENDING", url, expires, null);
    }

    public LinkStatus status(UUID id) {
        LinkStatus result = jdbc.query("""
            SELECT id,client_id,amount_pen,status,provider_url,expires_at_utc,sale_payment_id
            FROM membership_payment_links WHERE id=?
        """, (r,n)->new LinkStatus(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getBigDecimal(3),
                r.getString(4),r.getString(5),r.getTimestamp(6).toInstant(),r.getObject(7,UUID.class)), id)
                .stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Enlace no encontrado."));
        if ("PENDING".equals(result.status()) && !result.expiresAt().isAfter(clock.instant())) {
            jdbc.update("UPDATE membership_payment_links SET status='REVIEW', updated_at_utc=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id);
            return new LinkStatus(result.id(), result.clientId(), result.amountPen(), "REVIEW", result.url(), result.expiresAt(), result.salePaymentId());
        }
        return result;
    }

    /** The untrusted webhook supplies only an event ID. Provider API supplies the authoritative event and charge. */
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
        String linkId = firstNonBlank(charge.path("link_id").asText(""), charge.path("metadata").path("link_id").asText(""),
                data.path("link_id").asText(""));
        String description = charge.path("description").asText("");
        List<Pending> matching = jdbc.query("""
            SELECT id,client_id,plan_id,operation,amount_pen,provider_link_id,created_by_email,status,expires_at_utc
            FROM membership_payment_links WHERE provider_link_id=? OR ? LIKE '%' || id::text || '%'
        """, (r,n)->new Pending(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),
                MembershipQuoteRequest.Operation.valueOf(r.getString(4)),r.getBigDecimal(5),r.getString(6),
                r.getString(7),r.getString(8),r.getTimestamp(9).toInstant()), linkId, description);
        if (matching.size() != 1) return; // No provable correlation: keep pending for manual reconciliation.
        Pending pending = matching.getFirst();
        if (!"PEN".equals(charge.path("currency_code").asText()) ||
                charge.path("amount").asInt(-1) != pending.amountPen().movePointRight(2).intValueExact()) return;
        transactions.executeWithoutResult(ignored -> finalizeVerified(pending.id(), chargeId));
    }

    public void finalizeVerified(UUID id, String chargeId) {
        Pending pending = jdbc.query("""
            SELECT id,client_id,plan_id,operation,amount_pen,provider_link_id,created_by_email,status,expires_at_utc
            FROM membership_payment_links WHERE id=? FOR UPDATE
        """, (r,n)->new Pending(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),
                MembershipQuoteRequest.Operation.valueOf(r.getString(4)),r.getBigDecimal(5),r.getString(6),
                r.getString(7),r.getString(8),r.getTimestamp(9).toInstant()), id).stream().findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Enlace no encontrado."));
        if ("PAID".equals(pending.status())) return;
        if (!"PENDING".equals(pending.status()) && !"REVIEW".equals(pending.status())) return;
        var quote = memberships.quote(new MembershipQuoteRequest(pending.clientId(), pending.planId(), pending.operation()));
        if (quote.pricePen().compareTo(pending.amountPen()) != 0) {
            jdbc.update("UPDATE membership_payment_links SET status='REVIEW', provider_transaction_id=?,updated_at_utc=CURRENT_TIMESTAMP WHERE id=?", chargeId, id);
            return;
        }
        var sale = memberships.sellVerifiedOnline(new MembershipSaleRequest(pending.clientId(), pending.planId(),
                pending.operation(), quote.fingerprint(), pending.amountPen(), "CULQI", chargeId),
                "culqi_" + id.toString().replace("-", ""), pending.actorEmail());
        jdbc.update("""
            UPDATE membership_payment_links SET status='PAID',provider_transaction_id=?,sale_payment_id=?,updated_at_utc=CURRENT_TIMESTAMP WHERE id=?
        """, chargeId, sale.paymentId(), id);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
