package com.gymvip.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Endpoints para gestión de comunicados oficiales del club (gym_announcements) y outbox push.
 */
@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final JdbcTemplate jdbc;

    public AnnouncementController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AnnouncementDto(
            UUID id,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String message,
            String category,
            String targetAudience,
            boolean isActive,
            UUID facilityId,
            String createdBy,
            String createdAtUtc
    ) {}

    public record CreateAnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String message,
            String category,
            String targetAudience,
            UUID facilityId,
            boolean sendPush
    ) {}

    @GetMapping
    public List<AnnouncementDto> listAnnouncements() {
        return jdbc.query("""
            SELECT id, title, message, category, target_audience, is_active, facility_id, created_by, created_at_utc
            FROM gym_announcements
            WHERE is_active = true
            ORDER BY created_at_utc DESC LIMIT 50
        """, (r, n) -> new AnnouncementDto(
                r.getObject("id", UUID.class),
                r.getString("title"),
                r.getString("message"),
                r.getString("category"),
                r.getString("target_audience"),
                r.getBoolean("is_active"),
                r.getObject("facility_id", UUID.class),
                r.getString("created_by"),
                r.getTimestamp("created_at_utc") != null ? r.getTimestamp("created_at_utc").toInstant().toString() : ""
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @Transactional
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request, Authentication auth) {
        UUID id = UUID.randomUUID();
        String category = (request.category() != null && !request.category().isBlank()) ? request.category().toUpperCase() : "COMUNICADO";
        String audience = (request.targetAudience() != null && !request.targetAudience().isBlank()) ? request.targetAudience().toUpperCase() : "TODOS";
        String author = (auth != null) ? auth.getName() : "ADMIN";

        jdbc.update("""
            INSERT INTO gym_announcements (id, title, message, category, target_audience, is_active, facility_id, created_by)
            VALUES (?, ?, ?, ?, ?, true, ?, ?)
        """, id, request.title(), request.message(), category, audience, request.facilityId(), author);

        if (request.sendPush()) {
            String idempotencyKey = "announcement_push_" + id;
            jdbc.update("""
                INSERT INTO notification_outbox (id, idempotency_key, channel, recipient, title, payload, status, inbox_item_id)
                VALUES (?, ?, 'PUSH', 'ALL_MEMBERS', ?, ?, 'PENDING', ?)
                ON CONFLICT (idempotency_key) DO NOTHING
            """, UUID.randomUUID(), idempotencyKey, request.title(), request.message(), id.toString());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new AnnouncementDto(
                id, request.title(), request.message(), category, audience, true, request.facilityId(), author, new Date().toInstant().toString()
        ));
    }
}
