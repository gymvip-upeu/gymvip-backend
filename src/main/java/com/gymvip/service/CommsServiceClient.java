package com.gymvip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Cliente HTTP para comunicación entre gymvip-backend y gymvip-ms-comunicaciones.
 * HU-14 T08 y HU-18 T06.
 */
@Component
public class CommsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CommsServiceClient.class);

    private final RestClient restClient;
    private final String commsUrl;
    private final String serviceToken;

    public CommsServiceClient(
            @Value("${app.comms.url:http://localhost:8085}") String commsUrl,
            @Value("${app.comms.token:gymvip-internal-token-2026}") String serviceToken
    ) {
        this.commsUrl = commsUrl;
        this.serviceToken = serviceToken;
        this.restClient = RestClient.builder()
                .baseUrl(commsUrl)
                .defaultHeader("X-Service-Key", serviceToken)
                .build();
    }

    public record CommsDispatchResult(
            boolean dispatched,
            String deliveryId,
            String status,
            String errorMessage
    ) {}

    public CommsDispatchResult sendWhatsAppReminder(
            String requestId,
            String phone,
            String templateName,
            List<String> parameters,
            boolean consentWhatsapp,
            UUID clientId,
            String clientName,
            UUID membershipId
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "requestId", requestId,
                    "phoneNumber", phone != null ? phone : "",
                    "templateName", templateName,
                    "language", "es",
                    "parameters", parameters != null ? parameters : List.of(),
                    "consentWhatsapp", consentWhatsapp,
                    "clientId", clientId != null ? clientId.toString() : "",
                    "clientName", clientName != null ? clientName : "Socio",
                    "membershipId", membershipId != null ? membershipId.toString() : ""
            );

            var res = restClient.post()
                    .uri("/api/v1/messages/whatsapp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                    .toEntity(Map.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                var resBody = res.getBody();
                String extId = String.valueOf(resBody.get("externalMessageId"));
                String status = String.valueOf(resBody.get("status"));
                return new CommsDispatchResult(true, extId, status, null);
            }

            return new CommsDispatchResult(false, null, "FALLIDO", "Error retornado por microservicio: HTTP " + res.getStatusCode().value());

        } catch (Exception e) {
            log.warn("[COMMS CLIENT] No fue posible contactar a gymvip-comunicaciones en {}: {}", commsUrl, e.getMessage());
            return new CommsDispatchResult(false, null, "FALLIDO", "Microservicio de comunicaciones no disponible: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCommunications(String status, String channel, String search, int page, int size) {
        try {
            String uri = String.format("/api/v1/communications?page=%d&size=%d", page, size);
            if (status != null && !status.isBlank()) uri += "&status=" + status;
            if (channel != null && !channel.isBlank()) uri += "&channel=" + channel;
            if (search != null && !search.isBlank()) uri += "&search=" + search;

            var res = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);

            return res != null ? res : Map.of("content", List.of(), "totalElements", 0);

        } catch (Exception e) {
            log.warn("[COMMS CLIENT] Fallo al consultar comunicaciones: {}", e.getMessage());
            return Map.of(
                    "content", List.of(),
                    "totalElements", 0,
                    "warning", "Microservicio de comunicaciones desconectado o no accesible en " + commsUrl
            );
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> retryCommunication(UUID id) {
        try {
            var res = restClient.post()
                    .uri("/api/v1/communications/" + id + "/retry")
                    .retrieve()
                    .body(Map.class);
            return res != null ? res : Map.of("status", "RETRY_TRIGGERED");
        } catch (Exception e) {
            throw new IllegalStateException("Error al solicitar reintento al microservicio: " + e.getMessage(), e);
        }
    }
}
