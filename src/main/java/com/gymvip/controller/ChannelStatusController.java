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

    public ChannelStatusController(CommsServiceClient communications,
            @Value("${comms.push.enabled:true}") boolean pushEnabled) {
        this.communications=communications; this.pushEnabled=pushEnabled;
    }

    @GetMapping("/status")
    public Map<String,Object> getChannelStatus() {
        return Map.of("service","gymvip-backend","timestamp",Instant.now().toString(),"channels",Map.of(
            "whatsapp",communications.getWhatsAppChannelStatus(),
            "app",Map.of("channel","APP","status","AVAILABLE",
                "inbox",Map.of("status","AVAILABLE","requiresFirebase",false),
                // No working server FCM dispatcher is installed; an enabled flag cannot certify it.
                "push",Map.of("enabled",pushEnabled,"status",pushEnabled ? "UNCONFIGURED" : "DISABLED",
                    "connectionVerified",false,"message",pushEnabled ? "Pendiente de configuración y validación" : "Desactivado"))));
    }
}
