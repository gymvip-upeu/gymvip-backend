package com.gymvip.service;

import com.gymvip.exception.ConflictException;
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

    public IncidentService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Incident> list(String status) {
        if (status != null && !status.isBlank() && !List.of("ABIERTA", "CERRADA").contains(status))
            throw new IllegalArgumentException("Estado de incidencia no permitido.");
        String sql = "SELECT id,title,description,status,reported_by,created_at_utc,closed_by,closed_at_utc,close_reason FROM operational_incidents "
                + (status == null || status.isBlank() ? "" : "WHERE status=? ") + "ORDER BY created_at_utc DESC LIMIT 200";
        return status == null || status.isBlank() ? jdbc.query(sql, this::map)
                : jdbc.query(sql, this::map, status);
    }

    @Transactional
    public Incident create(CreateRequest request, String actor) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = now();
        jdbc.update("INSERT INTO operational_incidents(id,title,description,status,reported_by,created_at_utc) VALUES (?,?,?,'ABIERTA',?,?)",
                id, request.title().trim(), request.description().trim(), actor, now);
        audit.recordEvent(actor, "CREATE_INCIDENT", "OperationalIncident", id.toString(), "Incidencia registrada.");
        return find(id);
    }

    @Transactional
    public Incident close(UUID id, CloseRequest request, String actor) {
        if (request.reason().trim().isEmpty()) throw new IllegalArgumentException("Indica el motivo de cierre.");
        int changed = jdbc.update("UPDATE operational_incidents SET status='CERRADA',closed_by=?,closed_at_utc=?,close_reason=? WHERE id=? AND status='ABIERTA'",
                actor, now(), request.reason().trim(), id);
        if (changed != 1) throw new ConflictException("La incidencia no existe o ya fue cerrada.");
        audit.recordEvent(actor, "CLOSE_INCIDENT", "OperationalIncident", id.toString(), "Cierre registrado con motivo.");
        return find(id);
    }

    @Transactional(readOnly = true)
    public Incident find(UUID id) {
        return jdbc.query("SELECT id,title,description,status,reported_by,created_at_utc,closed_by,closed_at_utc,close_reason FROM operational_incidents WHERE id=?",
                this::map, id).stream().findFirst()
                .orElseThrow(() -> new com.gymvip.exception.ResourceNotFoundException("Incidencia no encontrada."));
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private Incident map(ResultSet rs, int ignored) throws SQLException {
        var closed = rs.getTimestamp("closed_at_utc");
        return new Incident(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getString("status"), rs.getString("reported_by"), rs.getTimestamp("created_at_utc").toLocalDateTime(),
                rs.getString("closed_by"), closed == null ? null : closed.toLocalDateTime(), rs.getString("close_reason"));
    }
}
