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
    private final RestClient statusClient;
    private final String commsUrl;
    private final String serviceToken;

    @org.springframework.beans.factory.annotation.Autowired
    public CommsServiceClient(
            @Value("${app.comms.url:http://localhost:8085}") String commsUrl,
            @Value("${app.comms.token:gymvip-internal-token-2026}") String serviceToken
    ) {
        this(commsUrl, serviceToken, null);
    }

    CommsServiceClient(String commsUrl, String serviceToken, RestClient.Builder statusBuilder) {
        this.commsUrl = commsUrl;
        this.serviceToken = serviceToken;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));
        var statusFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build());
        statusFactory.setReadTimeout(java.time.Duration.ofSeconds(3));
        this.statusClient = (statusBuilder == null ? RestClient.builder().requestFactory(statusFactory) : statusBuilder).baseUrl(commsUrl)
                .defaultHeader("X-Service-Key", serviceToken).build();
        this.restClient = RestClient.builder().requestFactory(factory)
                .baseUrl(commsUrl)
                .defaultHeader("X-Service-Key", serviceToken)
                .build();
    }


    public Map<String,Object> getWhatsAppChannelStatus() {
        try {
            var body=statusClient.get().uri("/api/v1/channels/status").retrieve().body(Map.class);
            if (body==null || !(body.get("channels") instanceof Map<?,?> channels)
                    || !(channels.get("whatsapp") instanceof Map<?,?> wa)
                    || !(wa.get("enabled") instanceof Boolean enabled)) throw new IllegalStateException("Invalid status");
            String status=String.valueOf(wa.get("status"));
            if (!Set.of("DISABLED","SIMULATION","UNCONFIGURED","CONFIGURED_REAL").contains(status)
                    || (!enabled && !status.equals("DISABLED"))
                    || (enabled && status.equals("DISABLED"))
                    || (status.equals("CONFIGURED_REAL") && !Boolean.TRUE.equals(wa.get("configurationComplete"))))
                throw new IllegalStateException("Invalid configuration status");
            return Map.of("channel","WHATSAPP","enabled",enabled,"status",status,
                    "source","comunicaciones","configurationComplete",Boolean.TRUE.equals(wa.get("configurationComplete")),
                    "connectionVerified",false);
        } catch (Exception ignored) {
            return Map.of("channel","WHATSAPP","status","UNVERIFIED","source","comunicaciones",
                    "message","No se pudo verificar","connectionVerified",false);
        }
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
                boolean success=!"FALLIDO".equals(status);
                if (extId.startsWith("wamid.sim_")) status="SIMULADO";
                return new CommsDispatchResult(success, extId, status, success ? null : "Proveedor rechazó el envío");
            }

            return new CommsDispatchResult(false, null, "FALLIDO", "Error retornado por microservicio: HTTP " + res.getStatusCode().value());

        } catch (Exception e) {
            log.warn("[COMMS CLIENT] No fue posible contactar a gymvip-comunicaciones en {}: {}", commsUrl, e.getMessage());
            return new CommsDispatchResult(false, null, "FALLIDO", "Servicio de WhatsApp no disponible o envío rechazado");
        }
    }

    public CommsDispatchResult sendReminderEmail(String email, String subject, String message, String template) {
        try {
            var result=restClient.post().uri("/api/v1/messages/email").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("to",email,"subject",subject,"template",template,"variables",Map.of("mensaje",message)))
                .retrieve().body(Map.class);
            if (result==null) return new CommsDispatchResult(false,null,"FALLIDO","Respuesta vacía del proveedor");
            String id=String.valueOf(result.get("deliveryId"));
            return new CommsDispatchResult(true,id,id.startsWith("sim_mail_") ? "SIMULADO" : "ACEPTADO_PROVEEDOR",null);
        } catch (Exception e) {
            log.warn("[COMMS CLIENT] Fallo al solicitar correo de recordatorio: {}",e.getClass().getSimpleName());
            return new CommsDispatchResult(false,null,"FALLIDO","Servicio de correo no disponible o envío rechazado");
        }
    }

    public CommsDispatchResult sendPasswordResetEmail(String toEmail, String resetLink, String rawToken, String recipientName) {
        try {
            Map<String, Object> variables = Map.of(
                    "link", resetLink != null ? resetLink : "",
                    "token", rawToken != null ? rawToken : "",
                    "recipientName", recipientName != null ? recipientName : "Socio VIP",
                    "expirationMinutes", 15
            );

            Map<String, Object> body = Map.of(
                    "to", toEmail,
                    "subject", "Recuperación de Contraseña — GYM VIP",
                    "template", "password_reset",
                    "variables", variables
            );

            var res = restClient.post()
                    .uri("/api/v1/messages/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                    .toEntity(Map.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                var resBody = res.getBody();
                String delId = String.valueOf(resBody.get("deliveryId"));
                String status = String.valueOf(resBody.get("status"));
                return new CommsDispatchResult(true, delId, status, null);
            }

            log.warn("[COMMS CLIENT] Microservicio respondió con error al enviar email de recuperación: HTTP {}", res.getStatusCode().value());
            return new CommsDispatchResult(false, null, "FALLIDO", "Error HTTP " + res.getStatusCode().value());

        } catch (Exception e) {
            log.info("[COMMS CLIENT] gymvip-comunicaciones no disponible en {}. Despacho de recuperación simulado localmente para {}.", commsUrl, toEmail);
            return new CommsDispatchResult(true, "local_sim_" + UUID.randomUUID().toString().substring(0, 8), "SIMULATED_LOCAL", null);
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
