package com.gymvip.training;

import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.service.MemberWorkspaceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class TrainingRepository {

    private final JdbcTemplate jdbc;
    private final AccountRepository accountRepository;
    private final MemberWorkspaceService memberWorkspaceService;
    private final MembershipRepository membershipRepository;

    public TrainingRepository(JdbcTemplate jdbc,
                              AccountRepository accountRepository,
                              MemberWorkspaceService memberWorkspaceService,
                              MembershipRepository membershipRepository) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
        this.memberWorkspaceService = memberWorkspaceService;
        this.membershipRepository = membershipRepository;
    }

    public List<Map<String, Object>> listExercises(String muscleGroup, String query) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, muscle_group, equipment, difficulty, technique, warnings FROM exercises WHERE is_active = TRUE ORDER BY name ASC"
        );
        String mg = muscleGroup != null ? muscleGroup.trim().toLowerCase(Locale.ROOT) : "";
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        return rows.stream().filter(r ->
                (mg.isEmpty() || String.valueOf(r.get("muscle_group")).toLowerCase(Locale.ROOT).contains(mg))
                        && (q.isEmpty() || String.valueOf(r.get("name")).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.get("equipment")).toLowerCase(Locale.ROOT).contains(q))
        ).map(this::normalizeKeys).toList();
    }

    public List<Map<String, Object>> listRoutinesForAccount(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email).orElse(null);
        UUID clientId = memberWorkspaceService.findOwnClient(email).orElse(null);

        List<Map<String, Object>> routines = jdbc.queryForList(
                "SELECT id, title, goal, level, duration_minutes, is_personal, assigned_client_id, notes, status FROM workout_routines WHERE status = 'ACTIVO' ORDER BY created_at DESC"
        );
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT i.id, i.routine_id, i.exercise_id, e.name AS exercise_name, e.muscle_group, e.equipment, " +
                        "i.sort_order, i.sets_count, i.reps_target, i.rest_seconds, i.trainer_notes " +
                        "FROM workout_routine_items i JOIN exercises e ON e.id = i.exercise_id ORDER BY i.sort_order ASC"
        );
        Map<String, List<Map<String, Object>>> itemsByRoutine = new HashMap<>();
        for (Map<String, Object> item : items) {
            itemsByRoutine.computeIfAbsent(String.valueOf(item.get("routine_id")), k -> new ArrayList<>())
                    .add(normalizeKeys(item));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : routines) {
            Object assigned = r.get("assigned_client_id");
            boolean isPublicTemplate = assigned == null && !Boolean.TRUE.equals(r.get("is_personal"));
            boolean assignedToSelf = assigned != null && clientId != null && assigned.equals(clientId);
            boolean isTrainer = account != null && account.getRoles().stream().anyMatch(role ->
                    role.equals("ENTRENADOR") || role.equals("ENTRENADOR_JEFE") || role.equals("ADMIN") || role.equals("SUPER_ADMIN") || role.equals("GERENTE_SEDE"));
            if (isPublicTemplate || assignedToSelf || isTrainer) {
                Map<String, Object> dto = normalizeKeys(r);
                dto.put("items", itemsByRoutine.getOrDefault(String.valueOf(r.get("id")), List.of()));
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createRoutine(String email, String title, String goal, String level, int durationMinutes,
                                             boolean isPersonal, UUID assignedClientId, String notes,
                                             List<Map<String, Object>> items) {
        Account actor = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID routineId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workout_routines(id, title, goal, level, duration_minutes, trainer_account_id, assigned_client_id, is_personal, notes, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')",
                routineId, title.trim(), goal.trim(), level != null ? level : "INTERMEDIO",
                Math.max(10, durationMinutes), actor.getId(), assignedClientId, isPersonal, notes
        );
        int idx = 1;
        if (items != null) {
            for (Map<String, Object> item : items) {
                Object exIdObj = item.get("exerciseId");
                if (exIdObj == null) continue;
                UUID exerciseId = UUID.fromString(String.valueOf(exIdObj));
                int sets = item.get("setsCount") instanceof Number n ? Math.max(1, n.intValue()) : 3;
                String reps = item.get("repsTarget") != null ? String.valueOf(item.get("repsTarget")) : "10-12";
                int rest = item.get("restSeconds") instanceof Number n ? Math.max(15, n.intValue()) : 90;
                String itemNote = item.get("trainerNotes") != null ? String.valueOf(item.get("trainerNotes")) : null;
                jdbc.update(
                        "INSERT INTO workout_routine_items(id, routine_id, exercise_id, sort_order, sets_count, reps_target, rest_seconds, trainer_notes) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), routineId, exerciseId, idx++, sets, reps, rest, itemNote
                );
            }
        }
        return Map.of("id", routineId, "title", title.trim(), "status", "ACTIVO");
    }

    @Transactional
    public Map<String, Object> recordWorkoutSessionIdempotent(String email, UUID routineId, String routineTitle,
                                                              int durationSeconds, String notes, String clientSyncId,
                                                              LocalDateTime startedAt, LocalDateTime completedAt,
                                                              List<Map<String, Object>> sets) {
        Account account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        if (clientSyncId != null && !clientSyncId.isBlank()) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id, routine_title, total_volume_kg, completed_at FROM workout_sessions WHERE client_sync_id = ?",
                    clientSyncId.trim()
            );
            if (!existing.isEmpty()) {
                Map<String, Object> dup = normalizeKeys(existing.get(0));
                dup.put("deduplicated", true);
                return dup;
            }
        }

        UUID sessionId = UUID.randomUUID();
        BigDecimal totalVolume = BigDecimal.ZERO;
        if (sets != null) {
            for (Map<String, Object> s : sets) {
                int reps = s.get("reps") instanceof Number n ? Math.max(0, n.intValue()) : 0;
                BigDecimal weight = s.get("weightKg") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
                totalVolume = totalVolume.add(weight.multiply(BigDecimal.valueOf(reps)));
            }
        }

        jdbc.update(
                "INSERT INTO workout_sessions(id, account_id, routine_id, routine_title, duration_seconds, total_volume_kg, notes, client_sync_id, started_at, completed_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sessionId, account.getId(), routineId, routineTitle != null ? routineTitle.trim() : "Entrenamiento libre",
                Math.max(0, durationSeconds), totalVolume, notes,
                clientSyncId != null && !clientSyncId.isBlank() ? clientSyncId.trim() : UUID.randomUUID().toString(),
                Timestamp.valueOf(startedAt != null ? startedAt : LocalDateTime.now()),
                Timestamp.valueOf(completedAt != null ? completedAt : LocalDateTime.now())
        );

        if (sets != null) {
            int setNum = 1;
            for (Map<String, Object> s : sets) {
                UUID exId = s.get("exerciseId") != null ? tryUuid(String.valueOf(s.get("exerciseId"))) : null;
                String exName = s.get("exerciseName") != null ? String.valueOf(s.get("exerciseName")) : "Ejercicio";
                int num = s.get("setNumber") instanceof Number n ? n.intValue() : setNum++;
                int reps = s.get("reps") instanceof Number n ? Math.max(0, n.intValue()) : 0;
                BigDecimal weight = s.get("weightKg") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
                jdbc.update(
                        "INSERT INTO workout_set_logs(id, session_id, exercise_id, exercise_name, set_number, reps_completed, weight_kg, completed) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)",
                        UUID.randomUUID(), sessionId, exId, exName, num, reps, weight
                );
            }
        }

        return Map.of("id", sessionId, "totalVolumeKg", totalVolume, "deduplicated", false);
    }

    public Map<String, Object> progressSummary(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        List<Map<String, Object>> sessions = jdbc.queryForList(
                "SELECT id, routine_title, duration_seconds, total_volume_kg, started_at, completed_at " +
                        "FROM workout_sessions WHERE account_id = ? ORDER BY completed_at DESC LIMIT 30",
                account.getId()
        );
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT l.exercise_name, MAX(l.weight_kg) AS max_weight_kg, MAX(l.reps_completed) AS max_reps " +
                        "FROM workout_set_logs l JOIN workout_sessions s ON s.id = l.session_id " +
                        "WHERE s.account_id = ? GROUP BY l.exercise_name ORDER BY max_weight_kg DESC LIMIT 10",
                account.getId()
        );
        BigDecimal cumulativeVolume = sessions.stream()
                .map(r -> r.get("total_volume_kg") instanceof BigDecimal b ? b : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalSessions", sessions.size(),
                "totalVolumeKg", cumulativeVolume,
                "recentSessions", sessions.stream().map(this::normalizeKeys).toList(),
                "personalRecords", records.stream().map(this::normalizeKeys).toList()
        );
    }

    public List<Map<String, Object>> listClasses(String email) {
        UUID clientId = memberWorkspaceService.findOwnClient(email).orElse(null);
        List<Map<String, Object>> classes = jdbc.queryForList(
                "SELECT c.id, c.branch_id, b.name AS branch_name, c.title, c.category, c.trainer_name, c.starts_at, c.duration_minutes, c.capacity, c.status, " +
                        "(SELECT COUNT(*) FROM class_reservations r WHERE r.class_id = c.id AND r.status = 'RESERVADA') AS reserved_count " +
                        "FROM gym_classes c LEFT JOIN branches b ON b.id = c.branch_id WHERE c.status = 'PROGRAMADA' ORDER BY c.starts_at ASC"
        );
        Set<String> myClassIds = new HashSet<>();
        if (clientId != null) {
            jdbc.queryForList(
                    "SELECT class_id FROM class_reservations WHERE client_id = ? AND status IN ('RESERVADA','LISTA_ESPERA')",
                    clientId
            ).forEach(r -> myClassIds.add(String.valueOf(r.get("class_id"))));
        }
        return classes.stream().map(row -> {
            Map<String, Object> dto = normalizeKeys(row);
            dto.put("reservedByMe", myClassIds.contains(String.valueOf(row.get("id"))));
            return dto;
        }).toList();
    }

    @Transactional
    public Map<String, Object> reserveClass(String email, UUID classId) {
        UUID clientId = memberWorkspaceService.ownClient(email);
        boolean activeMembership = membershipRepository.contract(clientId)
                .map(c -> "VIGENTE".equalsIgnoreCase(c.status()))
                .orElse(false);
        if (!activeMembership) {
            throw new ConflictException("Se requiere una membresía vigente para reservar clases.");
        }

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id, status FROM class_reservations WHERE class_id = ? AND client_id = ? AND status IN ('RESERVADA','LISTA_ESPERA')",
                classId, clientId
        );
        if (!existing.isEmpty()) {
            throw new ConflictException("Ya cuentas con una reserva activa para esta clase.");
        }

        Integer capacity = jdbc.queryForObject("SELECT capacity FROM gym_classes WHERE id = ? FOR UPDATE", Integer.class, classId);
        Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM class_reservations WHERE class_id = ? AND status = 'RESERVADA'", Integer.class, classId);
        if (capacity == null) {
            throw new IllegalArgumentException("Clase no encontrada.");
        }
        String status = (taken != null && taken >= capacity) ? "LISTA_ESPERA" : "RESERVADA";
        UUID reservationId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO class_reservations(id, class_id, client_id, status, reserved_at) VALUES (?, ?, ?, ?, NOW())",
                reservationId, classId, clientId, status
        );
        return Map.of("reservationId", reservationId, "classId", classId, "status", status);
    }

    @Transactional
    public Map<String, Object> createClass(String actorEmail, UUID branchId, String title, String category,
                                           String trainerName, LocalDateTime startsAt, int durationMinutes, int capacity) {
        Account actor = accountRepository.findByEmailIgnoreCase(actorEmail).orElseThrow();
        UUID classId = UUID.randomUUID();
        UUID effectiveBranch = branchId != null ? branchId : actor.getPrimaryBranchId();
        jdbc.update(
                "INSERT INTO gym_classes(id, organization_id, branch_id, title, category, trainer_name, trainer_account_id, starts_at, duration_minutes, capacity, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROGRAMADA')",
                classId, actor.getOrganizationId(), effectiveBranch,
                title.trim(), category != null ? category.trim() : "FUNCIONAL",
                trainerName != null && !trainerName.isBlank() ? trainerName.trim() : actorEmail,
                actor.getId(), Timestamp.valueOf(startsAt),
                Math.max(15, durationMinutes), Math.max(1, capacity)
        );
        return Map.of("id", classId, "title", title.trim(), "capacity", Math.max(1, capacity), "status", "PROGRAMADA");
    }

    @Transactional
    public Map<String, Object> cancelReservation(String email, UUID classId) {
        UUID clientId = memberWorkspaceService.ownClient(email);
        int updated = jdbc.update(
                "UPDATE class_reservations SET status = 'CANCELADA', cancelled_at = NOW() WHERE class_id = ? AND client_id = ? AND status IN ('RESERVADA','LISTA_ESPERA')",
                classId, clientId
        );
        return Map.of("cancelled", updated > 0, "classId", classId);
    }

    private UUID tryUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> normalizeKeys(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : row.entrySet()) {
            out.put(toCamelCase(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upperNext = false;
            }
        }
        return sb.toString();
    }
}
