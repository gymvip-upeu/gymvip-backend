package com.gymvip.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FacilityArchiveService {
    private final JdbcTemplate jdbc;

    public FacilityArchiveService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "America/Lima")
    @Transactional
    public void archiveExpiredFacilities() {
        var due = jdbc.query("""
            SELECT id FROM gym_services
            WHERE status = 'FUERA_DE_SERVICIO' AND is_archived = false
              AND status_changed_at_utc <= CURRENT_TIMESTAMP - INTERVAL '20 days'
            FOR UPDATE SKIP LOCKED
        """, (r, n) -> r.getObject("id", UUID.class));
        for (UUID id : due) {
            int updated = jdbc.update("""
                UPDATE gym_services SET is_archived = true, archived_at_utc = CURRENT_TIMESTAMP,
                    updated_at_utc = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'FUERA_DE_SERVICIO' AND is_archived = false
            """, id);
            if (updated == 1) {
                jdbc.update("INSERT INTO facility_status_history (id, facility_id, previous_status, next_status, actor, reason) VALUES (?, ?, 'FUERA_DE_SERVICIO', 'ARCHIVADO', 'SYSTEM', ?)",
                        UUID.randomUUID(), id, "20 días continuos fuera de servicio");
            }
        }
    }
}
