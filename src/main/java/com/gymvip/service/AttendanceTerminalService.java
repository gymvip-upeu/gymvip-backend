package com.gymvip.service;

import com.gymvip.dto.attendance.AttendanceDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class AttendanceTerminalService {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AttendanceTerminalService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TerminalDto> listTerminals() {
        return jdbc.sql("""
                SELECT id, name, venue_id, status, current_operator, last_active_at_utc
                FROM attendance_terminals
                ORDER BY name ASC
                """)
                .query((rs, rowNum) -> new TerminalDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("venue_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("current_operator"),
                        rs.getTimestamp("last_active_at_utc") != null ? rs.getTimestamp("last_active_at_utc").toInstant() : null
                ))
                .list();
    }

    public TerminalDto createTerminal(TerminalCreateRequest req, String actor) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String secret = "sec_" + HexFormat.of().formatHex(secretBytes);

        jdbc.sql("""
                INSERT INTO attendance_terminals(id, name, venue_id, status, current_operator, created_by, created_at_utc, shared_secret)
                VALUES (:id, :name, :venueId, 'INACTIVA', NULL, :actor, :now, :secret)
                """)
                .param("id", id)
                .param("name", req.name().trim())
                .param("venueId", req.venueId())
                .param("actor", actor)
                .param("now", java.sql.Timestamp.from(now))
                .param("secret", secret)
                .update();

        return new TerminalDto(id, req.name().trim(), req.venueId(), "INACTIVA", null, null);
    }

    public TerminalDto activateTerminal(UUID terminalId, String operator) {
        Instant now = clock.instant();
        int updated = jdbc.sql("""
                UPDATE attendance_terminals
                SET status = 'ACTIVA', current_operator = :operator, last_active_at_utc = :now
                WHERE id = :id AND status != 'REVOCADA'
                """)
                .param("id", terminalId)
                .param("operator", operator)
                .param("now", java.sql.Timestamp.from(now))
                .update();

        if (updated == 0) {
            throw new ConflictException("La terminal no existe o ha sido revocada por administración.");
        }

        return getTerminal(terminalId);
    }

    public TerminalDto deactivateTerminal(UUID terminalId, String operator) {
        int updated = jdbc.sql("""
                UPDATE attendance_terminals
                SET status = 'INACTIVA', current_operator = NULL
                WHERE id = :id AND status != 'REVOCADA'
                """)
                .param("id", terminalId)
                .update();

        if (updated == 0) {
            throw new ResourceNotFoundException("Terminal no encontrada.");
        }

        // Limpiar desafíos pendientes de la terminal desactivada
        jdbc.sql("DELETE FROM attendance_terminal_challenges WHERE terminal_id = :id")
                .param("id", terminalId)
                .update();

        return getTerminal(terminalId);
    }

    public ChallengeResponse generateChallenge(UUID terminalId, String operator) {
        var terminal = getTerminal(terminalId);
        if (!"ACTIVA".equalsIgnoreCase(terminal.status())) {
            throw new ConflictException("La terminal debe estar en estado ACTIVA para generar códigos QR de asistencia.");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(20); // 20 segundos de vigencia

        byte[] tokenBytes = new byte[24];
        random.nextBytes(tokenBytes);
        String randomHex = HexFormat.of().formatHex(tokenBytes);
        String challengeToken = "gymvip:challenge:v1:" + terminalId + ":" + randomHex;

        // Limpiar desafíos anteriores expirados de la terminal
        jdbc.sql("DELETE FROM attendance_terminal_challenges WHERE terminal_id = :tid AND expires_at_utc < :now")
                .param("tid", terminalId)
                .param("now", java.sql.Timestamp.from(issuedAt))
                .update();

        // Registrar nuevo desafío
        UUID challengeId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO attendance_terminal_challenges(id, terminal_id, challenge_token, issued_at_utc, expires_at_utc, created_by)
                VALUES (:id, :tid, :token, :issued, :expires, :operator)
                """)
                .param("id", challengeId)
                .param("tid", terminalId)
                .param("token", challengeToken)
                .param("issued", java.sql.Timestamp.from(issuedAt))
                .param("expires", java.sql.Timestamp.from(expiresAt))
                .param("operator", operator)
                .update();

        // Actualizar último pulso de actividad
        jdbc.sql("UPDATE attendance_terminals SET last_active_at_utc = :now WHERE id = :id")
                .param("now", java.sql.Timestamp.from(issuedAt))
                .param("id", terminalId)
                .update();

        return new ChallengeResponse(
                challengeToken,
                terminalId,
                terminal.name(),
                issuedAt,
                expiresAt,
                15 // Intervalo recomendado de refresco en frontend
        );
    }

    @Transactional(readOnly = true)
    public TerminalDto getTerminal(UUID terminalId) {
        return jdbc.sql("""
                SELECT id, name, venue_id, status, current_operator, last_active_at_utc
                FROM attendance_terminals
                WHERE id = :id
                """)
                .param("id", terminalId)
                .query((rs, rowNum) -> new TerminalDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("venue_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("current_operator"),
                        rs.getTimestamp("last_active_at_utc") != null ? rs.getTimestamp("last_active_at_utc").toInstant() : null
                ))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Terminal de asistencia no encontrada"));
    }
}
