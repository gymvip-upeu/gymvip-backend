package com.gymvip.service;

import com.gymvip.exception.ConflictException;
import com.gymvip.security.TenantScopeResolver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {
    public record CreateRequest(@NotBlank @Size(max = 160) String title,
                                @NotBlank @Size(max = 2000) String description) {}
    public record CloseRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record Incident(UUID id, String title, String description, String status,
                           String reportedBy, LocalDateTime createdAtUtc,
                           String closedBy, LocalDateTime closedAtUtc, String closeReason) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final TenantScopeResolver tenants;
    private static final String SCOPE = "organization_id=? AND (CAST(? AS uuid) IS NULL OR branch_id=?)";

    public IncidentService(JdbcTemplate jdbc, AuditService audit, Clock clock, TenantScopeResolver tenants) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<Incident> list(String status, String actor) {
        if (status != null && !status.isBlank() && !List.of("ABIERTA", "CERRADA").contains(status))
            throw new IllegalArgumentException("Estado de incidencia no permitido.");
        var scope = scope(actor);
        String sql = "SELECT * FROM operational_incidents WHERE " + SCOPE
                + " AND (CAST(? AS varchar) IS NULL OR status=?) ORDER BY created_at_utc DESC LIMIT 200";
        return jdbc.query(sql, this::map, scope.organization(), scope.branch(), scope.branch(), status, status);
    }

    @Transactional
    public Incident create(CreateRequest request, String actor) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = now();
        var scope = scope(actor);
        jdbc.update("INSERT INTO operational_incidents(id,title,description,status,reported_by,created_at_utc,organization_id,branch_id) VALUES (?,?,?,'ABIERTA',?,?,?,?)",
                id, request.title().trim(), request.description().trim(), actor, now, scope.organization(), scope.branch());
        audit.recordEvent(actor, "CREATE_INCIDENT", "OperationalIncident", id.toString(), "Incidencia registrada.");
        return find(id, scope);
    }

    @Transactional
    public Incident close(UUID id, CloseRequest request, String actor) {
        if (request.reason().trim().isEmpty()) throw new IllegalArgumentException("Indica el motivo de cierre.");
        var scope = scope(actor);
        int changed = jdbc.update("UPDATE operational_incidents SET status='CERRADA',closed_by=?,closed_at_utc=?,close_reason=? WHERE id=? AND status='ABIERTA' AND " + SCOPE,
                actor, now(), request.reason().trim(), id, scope.organization(), scope.branch(), scope.branch());
        if (changed != 1) throw new ConflictException("La incidencia no existe o ya fue cerrada.");
        audit.recordEvent(actor, "CLOSE_INCIDENT", "OperationalIncident", id.toString(), "Cierre registrado con motivo.");
        return find(id, scope);
    }

    private Incident find(UUID id, Scope scope) {
        return jdbc.query("SELECT * FROM operational_incidents WHERE id=? AND " + SCOPE,
                this::map, id, scope.organization(), scope.branch(), scope.branch()).stream().findFirst()
                .orElseThrow(() -> new com.gymvip.exception.ResourceNotFoundException("Incidencia no encontrada."));
    }

    private record Scope(UUID organization, UUID branch) {}

    private Scope scope(String email) {
        var actor = tenants.resolveActorByEmail(email);
        var organization = tenants.effectiveOrganizationId(actor, null);
        var branch = tenants.isCompanyWideRole(actor) ? null : tenants.effectiveBranchId(actor, null);
        return new Scope(organization, branch);
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private Incident map(ResultSet rs, int ignored) throws SQLException {
        var closed = rs.getTimestamp("closed_at_utc");
        return new Incident(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getString("status"), rs.getString("reported_by"), rs.getTimestamp("created_at_utc").toLocalDateTime(),
                rs.getString("closed_by"), closed == null ? null : closed.toLocalDateTime(), rs.getString("close_reason"));
    }
}
