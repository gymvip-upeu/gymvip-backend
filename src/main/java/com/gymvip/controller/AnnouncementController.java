package com.gymvip.controller;

import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.AuditService;
import com.gymvip.exception.ResourceNotFoundException;
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

    private final TenantScopeResolver scope;
    private final AuditService audit;

    public AnnouncementController(JdbcTemplate jdbc, TenantScopeResolver scope, AuditService audit) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.audit = audit;
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

    public record DraftRequest(@NotBlank @Size(max = 200) String title,
                               @NotBlank @Size(max = 2000) String message) {}

    public record Draft(UUID id, String title, String message, String category) {}

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('communications.drafts')")
    public List<Draft> listDrafts(Authentication auth) {
        var actor = scope.resolveActor(auth);
        UUID org = scope.effectiveOrganizationId(actor, null);
        UUID branch = scope.isCompanyWideRole(actor) ? null : scope.effectiveBranchId(actor, null);
        return jdbc.query("""
            SELECT id, title, message, category FROM gym_announcements
            WHERE NOT is_active AND draft_branch_id IS NOT NULL AND organization_id=?
                AND (CAST(? AS uuid) IS NULL OR draft_branch_id=?)
            ORDER BY created_at_utc DESC LIMIT 50
        """, (row, index) -> new Draft(row.getObject("id", UUID.class), row.getString("title"),
                row.getString("message"), row.getString("category")), org, branch, branch);
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('communications.drafts')")
    @Transactional
    public Draft createDraft(@Valid @RequestBody DraftRequest request, Authentication auth) {
        var actor = scope.resolveActor(auth);
        UUID org = scope.effectiveOrganizationId(actor, null);
        UUID branch = scope.effectiveBranchId(actor, null);
        var draft = new Draft(UUID.randomUUID(), request.title().trim(), request.message().trim(), "COMUNICADO");
        jdbc.update("""
            INSERT INTO gym_announcements (id, title, message, category, target_audience, is_active, created_by, organization_id, draft_branch_id)
            VALUES (?, ?, ?, ?, 'TODOS', false, ?, ?, ?)
        """, draft.id(), draft.title(), draft.message(), draft.category(), auth.getName(), org, branch);
        audit.recordEvent(auth.getName(), "CREATE_ANNOUNCEMENT_DRAFT", "Announcement", draft.id().toString(), "Borrador registrado.");
        return draft;
    }

    @PatchMapping("/drafts/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA')")
    @Transactional
    public void publishDraft(@PathVariable UUID id, Authentication auth) {
        UUID org = scope.effectiveOrganizationId(scope.resolveActor(auth), null);
        if (jdbc.update("""
            UPDATE gym_announcements SET is_active=true, created_at_utc=CURRENT_TIMESTAMP
            WHERE id=? AND organization_id=? AND draft_branch_id IS NOT NULL AND NOT is_active
        """, id, org) == 0) {
            throw new ResourceNotFoundException("Borrador pendiente no encontrado.");
        }
        audit.recordEvent(auth.getName(), "PUBLISH_ANNOUNCEMENT_DRAFT", "Announcement", id.toString(), "Publicado en la bandeja de avisos.");
    }

    @GetMapping
    public List<AnnouncementDto> listAnnouncements(Authentication auth) {
        UUID organization = scope.effectiveOrganizationId(scope.resolveActor(auth), null);
        return jdbc.query("""
            SELECT id, title, message, category, target_audience, is_active, facility_id, created_by, created_at_utc
            FROM gym_announcements
            WHERE is_active = true AND organization_id=?
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
        ), organization);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL','GERENTE_SEDE')")
    @Transactional
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request, Authentication auth) {
        UUID id = UUID.randomUUID();
        String category = (request.category() != null && !request.category().isBlank()) ? request.category().toUpperCase() : "COMUNICADO";
        String audience = (request.targetAudience() != null && !request.targetAudience().isBlank()) ? request.targetAudience().toUpperCase() : "TODOS";
        String author = auth.getName();
        var actor = scope.resolveActor(auth);
        UUID organization = scope.effectiveOrganizationId(actor, null);
        if (request.facilityId() != null) {
            UUID branch = jdbc.query("SELECT COALESCE(branch_id, ?) FROM gym_services WHERE id=?",
                    (row, index) -> row.getObject(1, UUID.class), TenantScopeResolver.DEFAULT_BRANCH_ID, request.facilityId())
                    .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Área no encontrada."));
            scope.effectiveBranchId(actor, branch);
        }

        jdbc.update("""
            INSERT INTO gym_announcements (id, title, message, category, target_audience, is_active, facility_id, created_by, organization_id)
            VALUES (?, ?, ?, ?, ?, true, ?, ?, ?)
        """, id, request.title(), request.message(), category, audience, request.facilityId(), author, organization);

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
