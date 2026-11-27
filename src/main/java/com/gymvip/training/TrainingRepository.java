package com.gymvip.training;

import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.TenantScopeResolver;
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
    private final TenantScopeResolver scope;

    public TrainingRepository(JdbcTemplate jdbc, AccountRepository accountRepository,
                              MemberWorkspaceService memberWorkspaceService, TenantScopeResolver scope) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
        this.memberWorkspaceService = memberWorkspaceService;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoutinesForAccount(String email) {
        Account account = scope.resolveActorByEmail(email);
        UUID clientId = memberWorkspaceService.findOwnClient(email).orElse(null);
        UUID organization = scope.effectiveOrganizationId(account, null);
        UUID branch = clientId == null ? account.getPrimaryBranchId()
                : scope.memberBranch(account, memberWorkspaceService.registeredBranch(clientId));
        boolean companyWide = !account.getRoles().contains("CLIENTE") && scope.isCompanyWideRole(account);
        boolean staffCanAssign = com.gymvip.security.PermissionCatalog.resolvePermissions(account.getRoles()).contains("training.routines.assign");
        List<Map<String, Object>> routines = jdbc.queryForList("""
                SELECT r.id,r.title,r.goal,r.level,r.duration_minutes,r.is_personal,r.assigned_client_id,r.notes,r.status
                FROM workout_routines r LEFT JOIN accounts creator ON creator.id=r.trainer_account_id
                WHERE r.status='ACTIVO' AND COALESCE(r.organization_id,creator.organization_id,?)=?
                AND (r.assigned_client_id=? OR r.trainer_account_id=? OR
                     (r.assigned_client_id IS NULL AND NOT r.is_personal AND
                        (? OR r.branch_id IS NULL OR r.branch_id=?)) OR
                     (? AND NOT r.is_personal AND (? OR r.branch_id=?)))
                ORDER BY r.created_at DESC
                """, TenantScopeResolver.DEFAULT_ORG_ID, organization, clientId, account.getId(), companyWide,
                branch, staffCanAssign, companyWide, branch);
        if (routines.isEmpty()) return List.of();
        UUID[] ids = routines.stream().map(r -> (UUID) r.get("id")).toArray(UUID[]::new);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id,i.routine_id,i.exercise_id,e.name AS exercise_name,e.muscle_group,e.equipment,
                       i.sort_order,i.sets_count,i.reps_target,i.rest_seconds,i.trainer_notes
                FROM workout_routine_items i JOIN exercises e ON e.id=i.exercise_id
                WHERE i.routine_id=ANY(?) ORDER BY i.sort_order
                """, (Object) ids);
        Map<String, List<Map<String, Object>>> itemsByRoutine = new HashMap<>();
        for (Map<String, Object> item : items) {
            itemsByRoutine.computeIfAbsent(String.valueOf(item.get("routine_id")), k -> new ArrayList<>())
                    .add(normalizeKeys(item));
        }
        return routines.stream().map(row -> {
            var result = normalizeKeys(row);
            result.put("items", itemsByRoutine.getOrDefault(String.valueOf(row.get("id")), List.of()));
            return result;
        }).toList();
    }

    @Transactional
    public Map<String, Object> createRoutine(String email, String title, String goal, String level, int durationMinutes,
                                             boolean isPersonal, UUID assignedClientId, String notes,
                                             List<Map<String, Object>> items) {
        Account actor = scope.resolveActorByEmail(email);
        UUID organization = scope.effectiveOrganizationId(actor, null);
        UUID branch = actor.getRoles().contains("CLIENTE")
                ? scope.memberBranch(actor, memberWorkspaceService.findOwnClient(email).map(memberWorkspaceService::registeredBranch).orElse(null))
                : scope.effectiveBranchId(actor, null);
        if (assignedClientId != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM clients c LEFT JOIN accounts a ON a.id=c.account_id
                WHERE c.id=? AND c.status='ACTIVO' AND COALESCE(a.organization_id,?)=?
                AND COALESCE(c.branch_id,a.primary_branch_id,?)=?)
                """, Boolean.class, assignedClientId, TenantScopeResolver.DEFAULT_ORG_ID, organization,
                TenantScopeResolver.DEFAULT_BRANCH_ID, branch))) {
            throw new org.springframework.security.access.AccessDeniedException("El socio no está asignado a esta sede.");
        }
        UUID routineId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workout_routines(id,organization_id,branch_id,title,goal,level,duration_minutes,trainer_account_id,
                    assigned_client_id,is_personal,notes,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVO')
                """, routineId, organization, branch, title.trim(), goal.trim(), level != null ? level : "INTERMEDIO",
                Math.max(10, durationMinutes), actor.getId(), assignedClientId, isPersonal, notes);
        int idx = 1;
        if (items != null) {
            for (Map<String, Object> item : items) {
                Object exIdObj = item.get("exerciseId");
                if (exIdObj == null) continue;
                UUID exerciseId = UUID.fromString(String.valueOf(exIdObj));
                if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM exercises WHERE id=? AND is_active AND
                            (organization_id IS NULL OR (organization_id=? AND branch_id=?)))
                        """, Boolean.class, exerciseId, organization, branch))) {
                    throw new IllegalArgumentException("El ejercicio no está publicado para la sede de esta rutina.");
                }
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
                    "SELECT id, routine_title, total_volume_kg, completed_at FROM workout_sessions WHERE client_sync_id = ? AND account_id = ?",
                    clientSyncId.trim(), account.getId()
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
