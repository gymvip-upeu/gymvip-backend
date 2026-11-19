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
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Paginación de comunicaciones inválida.");
        }
        String state = filter(status);
        String selectedChannel = filter(channel);
        if (state != null && !Set.of("SOLICITADO", "ACEPTADO_PROVEEDOR", "ENVIADO", "ENTREGADO",
                "LEIDO", "FALLIDO", "DESCONOCIDO").contains(state)) {
            throw new IllegalArgumentException("Estado de comunicación inválido.");
        }
        if (selectedChannel != null && !Set.of("APP", "WHATSAPP", "EMAIL").contains(selectedChannel)) {
            throw new IllegalArgumentException("Canal de comunicación inválido.");
        }
        String term = search == null ? "" : search.strip();
        Map<String, Object> result = commsClient.getCommunications(state, selectedChannel, term, page, size);
        if (result.containsKey("warning")) {
            String from = """
                    FROM sent_reminder_dispatches d JOIN clients c ON c.id = d.client_id
                    WHERE (CAST(? AS text) IS NULL OR d.status = ?)
                      AND (CAST(? AS text) IS NULL OR d.channel = ?)
                      AND position(lower(?) in lower(c.first_name || ' ' || c.last_name)) > 0
                    """;
            long count = jdbc.queryForObject("SELECT count(*) " + from, Long.class,
                    state, state, selectedChannel, selectedChannel, term);
            var fallbackRows = jdbc.queryForList("""
                    SELECT d.id, d.idempotency_key AS "requestId", d.channel,
                           c.first_name || ' ' || c.last_name AS "clientName",
                           CASE WHEN length(c.phone) > 3 THEN '***' || right(c.phone, 3) ELSE '***' END AS "recipientMasked",
                           d.rule_type AS "templateName", d.status,
                           CASE WHEN d.error_message IS NOT NULL THEN 'No se pudo confirmar el envío.' END AS "errorMessage",
                           d.created_at AS "createdAt"
                    """ + from + " ORDER BY d.created_at DESC, d.id LIMIT ? OFFSET ?",
                    state, state, selectedChannel, selectedChannel, term, size, (long) page * size);
            return ResponseEntity.ok(Map.of(
                    "content", fallbackRows,
                    "totalElements", count,
                    "totalPages", (count + size - 1) / size,
                    "currentPage", page,
                    "size", size,
                    "warning", result.get("warning")));
        }

        return ResponseEntity.ok(result);
    }

    private static String filter(String value) {
        if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value.trim())) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> retryCommunication(@PathVariable UUID id) {
        return ResponseEntity.ok(commsClient.retryCommunication(id));
    }
}
