package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpCulqiClient implements CulqiClient {
    private final RestClient client;
    private final String secret;
    private final boolean liveEnabled;

    public HttpCulqiClient(RestClient.Builder builder, @Value("${CULQI_SECRET_KEY:}") String secret,
                           @Value("${CULQI_LIVE_ENABLED:false}") boolean liveEnabled) {
        this.secret = secret;
        this.liveEnabled = liveEnabled;
        this.client = builder.baseUrl("https://api.culqi.com/v2").build();
    }

    @Override public boolean configured() {
        return secret.startsWith("sk_test_") || (liveEnabled && secret.startsWith("sk_live_"));
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("Culqi no está configurado en el backend.");
    }

    @Override public JsonNode createLink(UUID operationId, BigDecimal amountPen, Instant expiresAt) {
        requireConfigured();
        int cents = amountPen.movePointRight(2).intValueExact();
        if (cents < 600 || cents > 999900) throw new IllegalArgumentException("El importe está fuera del rango admitido por CulqiLink.");
        return client.post().uri("/links").header("Authorization", "Bearer " + secret)
                .header("x-culqi-product", "link").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", cents, "currency_code", "PEN",
                        "concept", "Membresía GYM VIP " + operationId,
                        "limit_uses", 1, "is_open_amount", false,
                        "payment_methods", List.of("tarjeta", "yape", "billetera"),
                        "expiration_date", expiresAt.getEpochSecond()
                )).retrieve().body(JsonNode.class);
    }

    @Override public JsonNode event(String eventId) {
        requireConfigured();
        return client.get().uri("/events/{id}", eventId).header("Authorization", "Bearer " + secret)
                .retrieve().body(JsonNode.class);
    }

    @Override public JsonNode charge(String chargeId) {
        requireConfigured();
        return client.get().uri("/charges/{id}", chargeId).header("Authorization", "Bearer " + secret)
                .retrieve().body(JsonNode.class);
    }
}
