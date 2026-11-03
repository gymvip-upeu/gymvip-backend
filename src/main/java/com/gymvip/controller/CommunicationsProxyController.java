package com.gymvip.controller;

import com.gymvip.service.CommsServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Gateway y Proxy para la consulta de estados de comunicaciones desde el frontend.
 * HU-18 T06.
 */
@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationsProxyController {

    private final CommsServiceClient commsClient;
    private final JdbcTemplate jdbc;

    public CommunicationsProxyController(CommsServiceClient commsClient, JdbcTemplate jdbc) {
        this.commsClient = commsClient;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<?> getCommunications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Intenta consultar primero el microservicio desacoplado
        Map<String, Object> result = commsClient.getCommunications(status, channel, search, page, size);

        // Fallback resiliente: si el microservicio está temporalmente desconectado, consulta sent_reminder_dispatches
        if (result.containsKey("warning")) {
            List<Map<String, Object>> fallbackRows = jdbc.queryForList("""
                SELECT d.id, d.idempotency_key AS "requestId", d.channel,
                       c.first_name || ' ' || c.last_name AS "clientName",
                       c.phone AS "recipientMasked",
                       d.rule_type AS "templateName",
                       d.status, d.external_delivery_id AS "externalMessageId",
                       d.error_message AS "errorMessage",
                       d.created_at AS "createdAt"
                FROM sent_reminder_dispatches d
                JOIN clients c ON c.id = d.client_id
                ORDER BY d.created_at DESC
                LIMIT 50
            """);

            return ResponseEntity.ok(Map.of(
                    "content", fallbackRows,
                    "totalElements", fallbackRows.size(),
                    "totalPages", 1,
                    "currentPage", 0,
                    "warning", result.get("warning")
            ));
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> retryCommunication(@PathVariable UUID id) {
        return ResponseEntity.ok(commsClient.retryCommunication(id));
    }
}
