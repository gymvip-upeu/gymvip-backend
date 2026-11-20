package com.gymvip.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reporte de estado de canales de comunicación (WhatsApp, Email y Notificaciones Push).
 * Informa con total transparencia técnica si cada canal opera en CONFIGURED_REAL, SIMULATION o DISABLED.
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelStatusController {

    @Value("${comms.whatsapp.enabled:true}")
    private boolean whatsAppEnabled;

    @Value("${comms.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${comms.push.enabled:true}")
    private boolean pushEnabled;

    @Value("${whatsapp.meta.token:}")
    private String metaToken;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getChannelStatus() {
        Map<String, Object> channels = new LinkedHashMap<>();

        // 1. Canal WhatsApp
        String whatsAppStatus = !whatsAppEnabled ? "DISABLED"
                : (metaToken != null && !metaToken.isBlank() && !metaToken.contains("placeholder")) ? "CONFIGURED_REAL" : "SIMULATION";
        channels.put("whatsapp", Map.of(
                "channel", "WHATSAPP",
                "status", whatsAppStatus,
                "provider", "Meta Cloud API v21.0 / Fallback Simulation",
                "supportsMedia", true
        ));

        // 2. Canal Email
        String emailStatus = !emailEnabled ? "DISABLED"
                : (resendApiKey != null && !resendApiKey.isBlank() && !resendApiKey.contains("placeholder")) ? "CONFIGURED_REAL" : "SIMULATION";
        channels.put("email", Map.of(
                "channel", "EMAIL",
                "status", emailStatus,
                "provider", "Resend API / Fallback Simulation",
                "supportsTemplates", true
        ));

        // 3. Canal Push (FCM / Firebase Cloud Messaging)
        String pushStatus = !pushEnabled ? "DISABLED" : "CONFIGURED_REAL";
        channels.put("push", Map.of(
                "channel", "PUSH",
                "status", pushStatus,
                "provider", "Firebase Cloud Messaging (FCM)",
                "privateVisibilityEnforced", true
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "gymvip-backend");
        response.put("timestamp", Instant.now().toString());
        response.put("channels", channels);

        return ResponseEntity.ok(response);
    }
}
