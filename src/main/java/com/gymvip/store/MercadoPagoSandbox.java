package com.gymvip.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
class MercadoPagoSandbox {
    private static final Set<String> CHECKOUT_HOSTS = Set.of("www.mercadopago.com.pe", "mercadopago.com.pe");
    private final RestClient client;
    private final boolean enabled;

    MercadoPagoSandbox(@Value("${app.store.mercado-pago-sandbox-enabled:false}") boolean enabled,
                       @Value("${app.store.mercado-pago-sandbox-token:}") String sandboxToken) {
        this.enabled = enabled && !sandboxToken.isBlank();
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl("https://api.mercadopago.com")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(sandboxToken))
                .build();
    }

    boolean enabled() {
        return enabled;
    }

    ProviderOrder create(UUID orderId, String productName, BigDecimal unitPrice, int quantity) {
        String total = unitPrice.multiply(BigDecimal.valueOf(quantity)).toPlainString();
        try {
            ProviderOrder order = client.post().uri("/v1/orders")
                    .header("X-Idempotency-Key", orderId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "type", "online",
                            "processing_mode", "manual",
                            "total_amount", total,
                            "external_reference", orderId.toString(),
                            "expiration_time", "P1D",
                            "items", List.of(Map.of("title", productName, "unit_price", unitPrice.toPlainString(),
                                    "quantity", quantity))))
                    .retrieve().body(ProviderOrder.class);
            validate(order, orderId, new BigDecimal(total));
            if (!validCheckoutUrl(order.checkoutUrl())) throw invalidResponse();
            return order;
        } catch (RestClientException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mercado Pago de prueba no está disponible.");
        }
    }

    ProviderOrder get(String providerId, UUID orderId, BigDecimal total) {
        try {
            ProviderOrder order = client.get().uri("/v1/orders/{id}", providerId)
                    .retrieve().body(ProviderOrder.class);
            validate(order, orderId, total);
            if (!providerId.equals(order.id())) throw invalidResponse();
            return order;
        } catch (RestClientException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago con Mercado Pago.");
        }
    }

    private static void validate(ProviderOrder order, UUID orderId, BigDecimal total) {
        if (order == null || order.id() == null || !order.id().startsWith("ORDTST") ||
                !orderId.toString().equals(order.externalReference()) || !"PEN".equals(order.currency()) ||
                order.totalAmount() == null || total.compareTo(order.totalAmount()) != 0) {
            throw invalidResponse();
        }
    }

    private static boolean validCheckoutUrl(String value) {
        if (value == null) return false;
        try {
            URI url = URI.create(value);
            return url.getHost() != null && CHECKOUT_HOSTS.contains(url.getHost()) &&
                    "https".equals(url.getScheme()) && url.getUserInfo() == null && url.getPort() == -1 &&
                    "/checkout/v1/redirect".equals(url.getPath());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Mercado Pago no devolvió una orden de prueba válida en soles.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderOrder(String id, String status, String currency,
                         @JsonProperty("status_detail") String statusDetail,
                         @JsonProperty("external_reference") String externalReference,
                         @JsonProperty("total_amount") BigDecimal totalAmount,
                         @JsonProperty("total_paid_amount") BigDecimal totalPaid,
                         @JsonProperty("checkout_url") String checkoutUrl) {}
}
