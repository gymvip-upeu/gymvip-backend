package com.gymvip.controller;

import com.google.firebase.messaging.FirebaseMessaging;
import com.gymvip.service.CommsServiceClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reporte consolidado de estado de canales de comunicación (WhatsApp, Email y App Móvil / Push).
 * Combina verificación en vivo hacia gymvip-comunicaciones y estado real de FirebaseMessaging.
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelStatusController {

    private final CommsServiceClient communications;
    private final boolean emailEnabled;
    private final boolean pushEnabled;
    private final boolean firebaseBeanAvailable;
    private final String firebaseServiceAccount;
    private final String resendApiKey;

    @Autowired
    public ChannelStatusController(
            CommsServiceClient communications,
            @Value("${comms.email.enabled:true}") boolean emailEnabled,
            @Value("${comms.push.enabled:true}") boolean pushEnabled,
            ObjectProvider<FirebaseMessaging> firebase,
            @Value("${FIREBASE_SERVICE_ACCOUNT_BASE64:}") String firebaseServiceAccount,
            @Value("${resend.api.key:}") String resendApiKey
    ) {
        this.communications = communications;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
        this.firebaseBeanAvailable = firebase != null && firebase.getIfAvailable() != null;
        this.firebaseServiceAccount = firebaseServiceAccount != null ? firebaseServiceAccount : "";
        this.resendApiKey = resendApiKey != null ? resendApiKey : "";
    }

    public ChannelStatusController(CommsServiceClient communications, boolean pushEnabled) {
        this(communications, true, pushEnabled, null, "", "");
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getChannelStatus() {
        Map<String, Object> channels = new LinkedHashMap<>();

        // 1. Canal WhatsApp (verificación en vivo contra gymvip-comunicaciones)
        channels.put("whatsapp", communications.getWhatsAppChannelStatus());

        // 2. Canal Email
        String emailStatus = !emailEnabled ? "DISABLED"
                : (!resendApiKey.isBlank() && !resendApiKey.contains("placeholder")) ? "CONFIGURED_REAL" : "SIMULATION";
        channels.put("email", Map.of(
                "channel", "EMAIL",
                "status", emailStatus,
                "provider", "Resend API / Fallback Simulation",
                "supportsTemplates", true
        ));

        // 3. Canal Push (FCM / Firebase Cloud Messaging) y Bandeja App
        boolean firebaseConfigured = firebaseBeanAvailable || !firebaseServiceAccount.isBlank();
        String pushStatus = !pushEnabled ? "DISABLED" : firebaseConfigured ? "CONFIGURED_REAL" : "UNCONFIGURED";
        String pushMessage = !pushEnabled
                ? "Desactivado"
                : firebaseConfigured
                        ? "Configurado para envío real; entrega sin verificar"
                        : "Pendiente de configuración y validación";

        channels.put("push", Map.of(
                "channel", "PUSH",
                "status", pushStatus,
                "provider", "Firebase Cloud Messaging (FCM)",
                "privateVisibilityEnforced", true,
                "connectionVerified", false
        ));

        channels.put("app", Map.of(
                "channel", "APP",
                "status", "AVAILABLE",
                "inbox", Map.of("status", "AVAILABLE", "requiresFirebase", false),
                "push", Map.of(
                        "enabled", pushEnabled,
                        "status", pushStatus,
                        "connectionVerified", false,
                        "message", pushMessage
                )
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "gymvip-backend");
        response.put("timestamp", Instant.now().toString());
        response.put("channels", channels);

        return ResponseEntity.ok(response);
    }
}
