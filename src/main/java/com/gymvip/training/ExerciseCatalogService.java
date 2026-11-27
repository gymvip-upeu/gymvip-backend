package com.gymvip.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.AuditService;
import com.gymvip.service.MemberWorkspaceService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class ExerciseCatalogService {
    private final JdbcTemplate jdbc;
    private final TenantScopeResolver scope;
    private final MemberWorkspaceService members;
    private final ObjectMapper json;
    private final AuditService audit;

    public ExerciseCatalogService(JdbcTemplate jdbc, TenantScopeResolver scope, MemberWorkspaceService members,
                                  ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.members = members;
        this.json = json;
        this.audit = audit;
    }

    public record Content(@NotBlank @Size(max = 120) String name,
                          @NotBlank @Size(max = 60) String muscleGroup,
                          @NotBlank @Size(max = 80) String equipment,
                          @NotBlank @Pattern(regexp = "PRINCIPIANTE|INTERMEDIO|AVANZADO") String difficulty,
                          @NotBlank @Size(max = 1000) String technique,
                          @Size(max = 500) String warnings,
                          @Size(max = 1000) String imageUrl,
                          @Size(max = 1000) String videoUrl) {}
    public record Exercise(UUID id, String name, String muscleGroup, String equipment, String difficulty,
                           String technique, String warnings, String imageUrl, String videoUrl) {}
    public record ManageItem(UUID id, UUID branchId, boolean published, boolean editable, Content content, Content draft) {}

    @Transactional(readOnly = true)
    public List<Exercise> published(String email, String muscleGroup, String q) {
        return readable(email, null, muscleGroup, q);
    }

    @Transactional(readOnly = true)
    public Exercise detail(String email, UUID id) {
        return readable(email, id, null, null).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Ejercicio no encontrado."));
    }

    private List<Exercise> readable(String email, UUID id, String muscleGroup, String q) {
        Account actor = scope.resolveActorByEmail(email);
        UUID organization = scope.effectiveOrganizationId(actor, null);
        UUID branch = actor.getRoles().contains("CLIENTE")
                ? scope.memberBranch(actor, members.findOwnClient(email).map(members::registeredBranch).orElse(null))
                : scope.isCompanyWideRole(actor) ? null : scope.effectiveBranchId(actor, null);
        return jdbc.query("""
                SELECT id,name,muscle_group,equipment,difficulty,technique,warnings,image_url,video_url
                FROM exercises
                WHERE is_active AND (organization_id IS NULL OR (organization_id=? AND (? OR branch_id=?)))
                  AND (CAST(? AS uuid) IS NULL OR id=?)
                  AND POSITION(LOWER(?) IN LOWER(muscle_group))>0 AND POSITION(LOWER(?) IN LOWER(name))>0
                ORDER BY name,id
                """, (rs, n) -> {
            Content c = content(rs);
            return new Exercise(rs.getObject("id", UUID.class), c.name(), c.muscleGroup(), c.equipment(),
                    c.difficulty(), c.technique(), c.warnings(), c.imageUrl(), c.videoUrl());
        }, organization, !actor.getRoles().contains("CLIENTE") && scope.isCompanyWideRole(actor), branch,
                id, id, muscleGroup == null ? "" : muscleGroup.trim(), q == null ? "" : q.trim());
    }

    @Transactional(readOnly = true)
    public List<ManageItem> manage(String email, UUID branchId) {
        UUID branch = scope.effectiveBranchId(scope.resolveActorByEmail(email), branchId);
        return jdbc.query("""
                SELECT e.*,d.content::text AS draft_content FROM exercises e
                LEFT JOIN exercise_drafts d ON d.exercise_id=e.id
                WHERE e.organization_id IS NULL OR e.branch_id=?
                ORDER BY e.name,e.id
                """, (rs, row) -> {
            var owner = rs.getObject("branch_id", UUID.class);
            return new ManageItem(rs.getObject("id", UUID.class), owner, rs.getBoolean("is_active"),
                    owner != null, content(rs), fromJson(rs.getString("draft_content")));
        }, branch);
    }

    @Transactional
    public UUID saveDraft(String email, UUID id, UUID branchId, Content input) {
        Account actor = scope.resolveActorByEmail(email);
        Content clean = new Content(input.name().trim(), input.muscleGroup().trim(), input.equipment().trim(),
                input.difficulty(), input.technique().trim(), input.warnings() == null ? null : input.warnings().trim(),
                safeUrl(input.imageUrl()), safeUrl(input.videoUrl()));
        if (id == null) {
            UUID branch = scope.effectiveBranchId(actor, branchId);
            id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO exercises(id,organization_id,branch_id,name,muscle_group,equipment,difficulty,technique,
                        warnings,image_url,video_url,is_active)
                    SELECT ?,b.organization_id,b.id,?,?,?,?,?,?,?,?,FALSE FROM branches b WHERE b.id=?
                    """, id, clean.name(), clean.muscleGroup(), clean.equipment(), clean.difficulty(), clean.technique(),
                    clean.warnings(), clean.imageUrl(), clean.videoUrl(), branch);
        } else {
            lockEditable(actor, id);
        }
        jdbc.update("""
                INSERT INTO exercise_drafts(exercise_id,content,updated_by) VALUES (?,CAST(? AS jsonb),?)
                ON CONFLICT (exercise_id) DO UPDATE SET content=EXCLUDED.content,updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
                """, id, toJson(clean), actor.getId());
        return id;
    }

    @Transactional
    public void publish(String email, UUID id) {
        Account actor = scope.resolveActorByEmail(email);
        lockEditable(actor, id);
        Content draft = jdbc.query("SELECT content::text FROM exercise_drafts WHERE exercise_id=?",
                        (rs, row) -> fromJson(rs.getString(1)), id)
                .stream().findFirst().orElseThrow(() -> new ConflictException("Guarda un borrador antes de publicar."));
        jdbc.update("""
                UPDATE exercises SET name=?,muscle_group=?,equipment=?,difficulty=?,technique=?,warnings=?,
                    image_url=?,video_url=?,is_active=TRUE WHERE id=?
                """, draft.name(), draft.muscleGroup(), draft.equipment(), draft.difficulty(), draft.technique(),
                draft.warnings(), draft.imageUrl(), draft.videoUrl(), id);
        jdbc.update("DELETE FROM exercise_drafts WHERE exercise_id=?", id);
        audit.recordEvent(email, "PUBLISH_EXERCISE", "Exercise", id.toString(), "Ficha de ejercicio publicada");
    }

    private void lockEditable(Account actor, UUID id) {
        var rows = jdbc.queryForList("SELECT organization_id,branch_id FROM exercises WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Ejercicio no encontrado.");
        var owner = (UUID) rows.getFirst().get("organization_id");
        var branch = (UUID) rows.getFirst().get("branch_id");
        if (owner == null || branch == null) {
            throw new AccessDeniedException("La ficha compartida es de solo lectura. Crea una versión para tu sede.");
        }
        scope.effectiveOrganizationId(actor, owner);
        scope.effectiveBranchId(actor, branch);
    }

    private static Content content(ResultSet rs) throws SQLException {
        return new Content(rs.getString("name"), rs.getString("muscle_group"), rs.getString("equipment"),
                rs.getString("difficulty"), rs.getString("technique"), rs.getString("warnings"),
                rs.getString("image_url"), rs.getString("video_url"));
    }

    private static String safeUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.trim());
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {}
        throw new IllegalArgumentException("Los medios deben usar un enlace HTTPS válido.");
    }

    private Content fromJson(String value) {
        if (value == null) return null;
        try {
            return json.readValue(value, Content.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("El borrador no se pudo cargar.", ex);
        }
    }

    private String toJson(Content value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("El borrador no se pudo guardar.", ex);
        }
    }
}
