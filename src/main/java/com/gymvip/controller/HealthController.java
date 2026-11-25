package com.gymvip.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de comprobación de salud accesible sin autenticación para monitoreo en Render / balanceadores.
 * Diferencia liveness de readiness con verificación acotada a PostgreSQL (SELECT 1).
 */
@RestController
@RequestMapping
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "gymvip-backend");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/health", "/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "gymvip-backend");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/health/liveness", "/health/liveness"})
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("check", "liveness");
        body.put("service", "gymvip-backend");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/health/readiness", "/health/readiness"})
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("check", "readiness");
        body.put("service", "gymvip-backend");
        body.put("timestamp", Instant.now().toString());
        body.put("revision", System.getenv().getOrDefault("APP_GIT_COMMIT",
                System.getenv().getOrDefault("RENDER_GIT_COMMIT", "unknown")));

        try {
            jdbcTemplate.setQueryTimeout(3);
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                body.put("status", "UP");
                body.put("database", "UP");
                return ResponseEntity.ok(body);
            }
        } catch (Exception ex) {
            body.put("status", "DOWN");
            body.put("database", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        body.put("status", "DOWN");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping({"/api/v1/system/status", "/system/status"})
    public ResponseEntity<Map<String, Object>> systemStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "gymvip-backend");
        body.put("version", "1.0.0");
        body.put("timestamp", Instant.now().toString());

        // Medición de latencia de base de datos
        long start = System.currentTimeMillis();
        boolean dbOk = false;
        try {
            jdbcTemplate.setQueryTimeout(3);
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbOk = (result != null && result == 1);
        } catch (Exception ignored) {}
        long latencyMs = System.currentTimeMillis() - start;

        body.put("status", dbOk ? "HEALTHY" : "DEGRADED");

        Map<String, Object> dbInfo = new LinkedHashMap<>();
        dbInfo.put("status", dbOk ? "UP" : "DOWN");
        dbInfo.put("latencyMs", latencyMs);
        dbInfo.put("engine", detectDatabaseEngine());
        body.put("database", dbInfo);

        // Métricas de memoria JVM
        Runtime runtime = Runtime.getRuntime();
        long totalMb = runtime.totalMemory() / (1024 * 1024);
        long freeMb = runtime.freeMemory() / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        Map<String, Object> jvmInfo = new LinkedHashMap<>();
        jvmInfo.put("totalMemoryMb", totalMb);
        jvmInfo.put("freeMemoryMb", freeMb);
        jvmInfo.put("usedMemoryMb", totalMb - freeMb);
        jvmInfo.put("maxMemoryMb", maxMb);
        jvmInfo.put("availableProcessors", runtime.availableProcessors());
        body.put("jvm", jvmInfo);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("dataImportMultiSheet", "ACTIVE");
        features.put("dashboardAnalytics", "ACTIVE");
        features.put("rateLimiting", "ACTIVE");
        features.put("caching", "ENABLED");
        body.put("features", features);

        return ResponseEntity.ok(body);
    }

    private String detectDatabaseEngine() {
        try {
            String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
            String latestFlyway = jdbcTemplate.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                    String.class
            );
            String pgPart = (serverVersion != null && !serverVersion.isBlank()) ? "PostgreSQL " + serverVersion.split(" ")[0] : "PostgreSQL";
            return (latestFlyway != null && !latestFlyway.isBlank()) ? pgPart + " (Flyway V" + latestFlyway + ")" : pgPart;
        } catch (Exception ignored) {
            return "PostgreSQL";
        }
    }
}
