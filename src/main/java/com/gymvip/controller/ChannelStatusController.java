package com.gymvip.controller;

import com.gymvip.service.CommsServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/channels")
public class ChannelStatusController {
    private final CommsServiceClient communications;
    private final boolean pushEnabled;
    private final boolean firebaseConfigured;

    @org.springframework.beans.factory.annotation.Autowired
    public ChannelStatusController(CommsServiceClient communications,
            @Value("${comms.push.enabled:true}") boolean pushEnabled,
            org.springframework.beans.factory.ObjectProvider<com.google.firebase.messaging.FirebaseMessaging> firebase) {
        this.communications=communications; this.pushEnabled=pushEnabled;
        this.firebaseConfigured=firebase.getIfAvailable()!=null;
    }

    public ChannelStatusController(CommsServiceClient communications, boolean pushEnabled) {
        this.communications=communications; this.pushEnabled=pushEnabled; this.firebaseConfigured=false;
    }

    @GetMapping("/status")
    public Map<String,Object> getChannelStatus() {
        return Map.of("service","gymvip-backend","timestamp",Instant.now().toString(),"channels",Map.of(
            "whatsapp",communications.getWhatsAppChannelStatus(),
            "app",Map.of("channel","APP","status","AVAILABLE",
                "inbox",Map.of("status","AVAILABLE","requiresFirebase",false),
                // A configured SDK is not a delivery receipt.
                "push",Map.of("enabled",pushEnabled,"status",!pushEnabled ? "DISABLED" : firebaseConfigured ? "CONFIGURED_REAL" : "UNCONFIGURED",
                    "connectionVerified",false,"message",!pushEnabled ? "Desactivado" : firebaseConfigured ? "Configurado para envío real; entrega sin verificar" : "Pendiente de configuración y validación"))));
    }
}
