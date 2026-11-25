package com.gymvip.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerUnitTest {

    @Test
    void systemStatusReportsDynamicPostgreSqlAndFlywayVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SHOW server_version", String.class)).thenReturn("18.6 (Debian 18.6-1)");
        when(jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                String.class
        )).thenReturn("28");

        HealthController controller = new HealthController(jdbc);
        var response = controller.systemStatus();

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) response.getBody().get("database");
        assertEquals("UP", db.get("status"));
        assertEquals("PostgreSQL 18.6 (Flyway V28)", db.get("engine"));
    }

    @Test
    void rootEndpointReturnsMinimalUpStatus() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HealthController controller = new HealthController(jdbc);

        var response = controller.root();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("gymvip-backend", response.getBody().get("service"));
        assertEquals("UP", response.getBody().get("status"));
    }
}
