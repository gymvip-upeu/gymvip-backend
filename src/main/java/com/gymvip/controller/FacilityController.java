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
 * Endpoints administrativos y operativos para Áreas e Instalaciones del Gimnasio (HU-34).
 */
@RestController
@RequestMapping("/api/v1/facilities")
public class FacilityController {

    private final JdbcTemplate jdbc;

    public FacilityController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FacilityDto(
            UUID id,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 150) String schedule,
            @NotBlank @Size(max = 100) String location,
            boolean isIncluded,
            String status,
            String imageUrl,
            String includedPlanTypes,
            int displayOrder,
            boolean isArchived
    ) {}

    public record StatusUpdateRequest(
            @NotBlank String status,
            String reason,
            boolean notifyMembers
    ) {}

    @GetMapping
    public List<FacilityDto> listFacilities() {
        return listByArchive(false);
    }

    @GetMapping("/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FacilityDto> listArchive() {
        return listByArchive(true);
    }

    private List<FacilityDto> listByArchive(boolean archived) {
        return jdbc.query("""
            SELECT id, name, description, schedule, location, is_included,
                   COALESCE(status, 'DISPONIBLE') AS status, image_url,
                   COALESCE(included_plan_types, 'TODOS') AS included_plan_types,
                   display_order, COALESCE(is_archived, false) AS is_archived
            FROM gym_services
            WHERE is_archived = ?
            ORDER BY display_order ASC, name ASC
        """, (r, n) -> new FacilityDto(
                r.getObject("id", UUID.class),
                r.getString("name"),
                r.getString("description"),
                r.getString("schedule"),
                r.getString("location"),
                r.getBoolean("is_included"),
                r.getString("status"),
                r.getString("image_url"),
                r.getString("included_plan_types"),
                r.getInt("display_order"),
                r.getBoolean("is_archived")
        ), archived);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<FacilityDto> createFacility(@Valid @RequestBody FacilityDto request, Authentication auth) {
        UUID id = UUID.randomUUID();
        String status = (request.status() != null && !request.status().isBlank()) ? request.status().toUpperCase() : "DISPONIBLE";
        String planTypes = (request.includedPlanTypes() != null && !request.includedPlanTypes().isBlank()) ? request.includedPlanTypes() : "TODOS";

        jdbc.update("""
            INSERT INTO gym_services (id, name, description, schedule, location, is_included, status, image_url, included_plan_types, display_order, is_archived, updated_at_utc, status_changed_at_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, id, request.name(), request.description(), request.schedule(), request.location(), request.isIncluded(), status, request.imageUrl(), planTypes, request.displayOrder());

        return ResponseEntity.status(HttpStatus.CREATED).body(new FacilityDto(
                id, request.name(), request.description(), request.schedule(), request.location(),
                request.isIncluded(), status, request.imageUrl(), planTypes, request.displayOrder(), false
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<FacilityDto> updateFacility(@PathVariable UUID id, @Valid @RequestBody FacilityDto request, Authentication auth) {
        String status = (request.status() != null && !request.status().isBlank()) ? request.status().toUpperCase() : "DISPONIBLE";
        String planTypes = (request.includedPlanTypes() != null && !request.includedPlanTypes().isBlank()) ? request.includedPlanTypes() : "TODOS";

        int updated = jdbc.update("""
            UPDATE gym_services
            SET name = ?, description = ?, schedule = ?, location = ?, is_included = ?,
                status = ?, image_url = ?, included_plan_types = ?, display_order = ?,
                is_archived = ?, updated_at_utc = CURRENT_TIMESTAMP,
                status_changed_at_utc = CASE WHEN status IS DISTINCT FROM ? THEN CURRENT_TIMESTAMP ELSE status_changed_at_utc END
            WHERE id = ?
        """, request.name(), request.description(), request.schedule(), request.location(), request.isIncluded(),
                status, request.imageUrl(), planTypes, request.displayOrder(), request.isArchived(), status, id);

        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new FacilityDto(
                id, request.name(), request.description(), request.schedule(), request.location(),
                request.isIncluded(), status, request.imageUrl(), planTypes, request.displayOrder(), request.isArchived()
        ));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request, Authentication auth) {
        String newStatus = request.status().toUpperCase().trim();
        if (!List.of("DISPONIBLE", "MANTENIMIENTO", "FUERA_DE_SERVICIO").contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado inválido. Opciones válidas: DISPONIBLE, MANTENIMIENTO, FUERA_DE_SERVICIO."));
        }

        var facilities = jdbc.queryForList("SELECT name, status FROM gym_services WHERE id = ? AND is_archived = false", id);
        if (facilities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String facilityName = (String) facilities.get(0).get("name");
        String previousStatus = (String) facilities.get(0).get("status");

        jdbc.update("UPDATE gym_services SET status = ?, status_changed_at_utc = CURRENT_TIMESTAMP, updated_at_utc = CURRENT_TIMESTAMP WHERE id = ?", newStatus, id);
        jdbc.update("INSERT INTO facility_status_history (id, facility_id, previous_status, next_status, actor, reason) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, previousStatus, newStatus, auth.getName(), request.reason());

        // Si se solicita notificar a miembros ante mantenimiento o interrupción, crear anuncio y outbox
        if (request.notifyMembers() && !newStatus.equals("DISPONIBLE")) {
            String noticeTitle = "Aviso Operativo: " + facilityName;
            String noticeMessage = (request.reason() != null && !request.reason().isBlank())
                    ? request.reason().trim()
                    : "El área " + facilityName + " se encuentra actualmente en estado de " + newStatus.replace("_", " ") + ".";

            UUID noticeId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO gym_announcements (id, title, message, category, target_audience, is_active, facility_id, created_by)
                VALUES (?, ?, ?, 'MANTENIMIENTO', 'TODOS', true, ?, ?)
            """, noticeId, noticeTitle, noticeMessage, id, auth.getName());

            // Registro en Notification Outbox para envío push
            String idempotencyKey = "notice_facility_" + id + "_" + System.currentTimeMillis();
            jdbc.update("""
                INSERT INTO notification_outbox (id, idempotency_key, channel, recipient, title, payload, status, inbox_item_id)
                VALUES (?, ?, 'PUSH', 'ALL_ACTIVE_MEMBERS', ?, ?, 'PENDING', ?)
                ON CONFLICT (idempotency_key) DO NOTHING
            """, UUID.randomUUID(), idempotencyKey, noticeTitle, noticeMessage,noticeId.toString());
        }

        return ResponseEntity.ok(Map.of("id", id, "status", newStatus, "updated", true));
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> restore(@PathVariable UUID id, Authentication auth) {
        int changed = jdbc.update("""
            UPDATE gym_services SET is_archived = false, archived_at_utc = NULL,
                status = 'MANTENIMIENTO', status_changed_at_utc = CURRENT_TIMESTAMP,
                updated_at_utc = CURRENT_TIMESTAMP
            WHERE id = ? AND is_archived = true
        """, id);
        if (changed == 0) return ResponseEntity.notFound().build();
        jdbc.update("INSERT INTO facility_status_history (id, facility_id, previous_status, next_status, actor, reason) VALUES (?, ?, ?, 'MANTENIMIENTO', ?, ?)",
                UUID.randomUUID(), id, "ARCHIVADO", auth.getName(), "Restauración administrativa");
        return ResponseEntity.ok(Map.of("id", id, "status", "MANTENIMIENTO", "isArchived", false));
    }
}
