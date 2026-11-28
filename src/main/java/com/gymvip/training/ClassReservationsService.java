package com.gymvip.training;

import com.gymvip.entity.Account;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.MemberWorkspaceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ClassReservationsService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final JdbcTemplate jdbc;
    private final TenantScopeResolver scope;
    private final MemberWorkspaceService members;
    private final MembershipRepository memberships;
    private final Clock clock;

    public ClassReservationsService(JdbcTemplate jdbc, TenantScopeResolver scope, MemberWorkspaceService members,
                                    MembershipRepository memberships, Clock clock) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.members = members;
        this.memberships = memberships;
        this.clock = clock;
    }

    public record ClassItem(UUID id, UUID branchId, String branchName, String title, String category,
                            String trainerName, String startsAt, int durationMinutes, int capacity, String status,
                            int reservedCount, boolean reservedByMe, String myReservationStatus) {}
    public record ReservationItem(UUID id, String memberName, String status, String reservedAt, String cancelledAt) {}
    public record ClassNotice(UUID id, UUID classId, String classTitle, String branchName, String eventType, String createdAt) {}
    public record Trainer(UUID id, String name) {}
    private record ClassRow(UUID id, UUID organizationId, UUID branchId, UUID trainerId, String title,
                            LocalDateTime startsAt, int capacity, String status) {}
    private record Reservation(UUID id, UUID clientId, String status) {}

    @Transactional(readOnly = true)
    public List<ClassItem> list(String email) {
        Account actor = scope.resolveActorByEmail(email);
        boolean member = actor.getRoles().contains("CLIENTE");
        UUID clientId = member ? members.ownClient(email) : null;
        boolean companyWide = !member && scope.isCompanyWideRole(actor);
        UUID branchId = member ? scope.memberBranch(actor, members.registeredBranch(clientId))
                : companyWide ? null : scope.effectiveBranchId(actor, null);
        if (member && branchId == null) return List.of();
        return jdbc.query("""
                SELECT c.id,c.branch_id,b.name AS branch_name,c.title,c.category,c.trainer_name,c.starts_at,
                    c.duration_minutes,c.capacity,c.status,
                    (SELECT COUNT(*) FROM class_reservations r WHERE r.class_id=c.id AND r.status='RESERVADA') AS reserved_count,
                    r.status AS own_status
                FROM gym_classes c
                JOIN branches b ON b.id=c.branch_id AND b.status='ACTIVO'
                LEFT JOIN class_reservations r ON r.class_id=c.id AND r.client_id=? AND r.status IN ('RESERVADA','LISTA_ESPERA')
                WHERE b.organization_id=? AND (? OR c.branch_id=?)
                  AND (NOT ? OR (c.status='PROGRAMADA' AND c.starts_at>?))
                ORDER BY c.starts_at,c.id
                """, (rs, row) -> {
            String status = rs.getString("own_status");
            return new ClassItem(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
                    rs.getString("branch_name"), rs.getString("title"), rs.getString("category"),
                    rs.getString("trainer_name"), lima(rs.getObject("starts_at", LocalDateTime.class)),
                    rs.getInt("duration_minutes"), rs.getInt("capacity"), rs.getString("status"),
                    rs.getInt("reserved_count"), status != null, status);
        }, clientId, scope.effectiveOrganizationId(actor, null), companyWide, branchId, member, now());
    }

    @Transactional(readOnly = true)
    public List<Trainer> trainers(String email, UUID requestedBranch) {
        Account actor = scope.resolveActorByEmail(email);
        UUID branch = scope.effectiveBranchId(actor, requestedBranch);
        return jdbc.query("""
                SELECT a.id, COALESCE(NULLIF(TRIM(CONCAT_WS(' ', a.first_name,a.last_name)),''),'Entrenador') AS name
                FROM accounts a JOIN branches b ON b.id=a.primary_branch_id
                WHERE b.id=? AND a.organization_id=b.organization_id AND a.status='ACTIVO'
                AND EXISTS (SELECT 1 FROM account_roles ar WHERE ar.account_id=a.id AND ar.role_id IN ('ENTRENADOR','ENTRENADOR_JEFE'))
                ORDER BY name,a.id
                """, (rs, i) -> new Trainer(rs.getObject(1, UUID.class), rs.getString(2)), branch);
    }

    @Transactional
    public Map<String, Object> create(String email, UUID requestedBranch, String title, String category,
                                      UUID trainerId, String trainerName, LocalDateTime startsAt, int duration, int capacity) {
        Account actor = scope.resolveActorByEmail(email);
        UUID branch = scope.effectiveBranchId(actor, requestedBranch);
        if (!startsAt.isAfter(now())) throw new IllegalArgumentException("La clase debe comenzar en una fecha futura.");
        String name = trainerName;
        if (trainerId != null) {
            UUID selectedTrainer = trainerId;
            name = trainers(email, branch).stream().filter(coach -> coach.id().equals(selectedTrainer)).map(Trainer::name)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("El entrenador debe estar activo en esa sede."));
        } else if (actor.getRoles().contains("ENTRENADOR") || actor.getRoles().contains("ENTRENADOR_JEFE")) {
            trainerId = actor.getId();
        } else {
            throw new IllegalArgumentException("Selecciona un entrenador asignado a esa sede.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gym_classes(id,organization_id,branch_id,title,category,trainer_name,trainer_account_id,
                    starts_at,duration_minutes,capacity,status)
                SELECT ?,b.organization_id,b.id,?,?,?,?,?,?,?,'PROGRAMADA' FROM branches b WHERE b.id=?
                """, id, title.trim(), category.trim(), name == null || name.isBlank() ? "Entrenador" : name.trim(),
                trainerId, startsAt, duration, capacity, branch);
        return Map.of("id", id, "status", "PROGRAMADA");
    }

    @Transactional
    public Map<String, Object> reserve(String email, UUID classId) {
        UUID client = members.ownClient(email);
        Account actor = scope.resolveActorByEmail(email);
        ClassRow course = loadClass(classId, true);
        requireMemberScope(actor, client, course);
        if (!"PROGRAMADA".equals(course.status()) || !course.startsAt().isAfter(now())) {
            throw new ConflictException("Las reservas de esta clase ya cerraron.");
        }
        if (!hasCurrentMembership(client)) {
            throw new ConflictException("Se requiere una membresía vigente para reservar clases.");
        }
        var current = openReservation(classId, client);
        if (current != null) return reservationResult(current, classId);
        Integer occupied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_reservations WHERE class_id=? AND status='RESERVADA'", Integer.class, classId);
        String status = occupied != null && occupied >= course.capacity() ? "LISTA_ESPERA" : "RESERVADA";
        Reservation reservation = new Reservation(UUID.randomUUID(), client, status);
        jdbc.update("INSERT INTO class_reservations(id,class_id,client_id,status,reserved_at) VALUES (?,?,?,?,?)",
                reservation.id(), classId, client, status, now());
        notifyReservation(course, reservation, status);
        return reservationResult(reservation, classId);
    }

    @Transactional
    public Map<String, Object> cancel(String email, UUID classId) {
        UUID client = members.ownClient(email);
        ClassRow course = loadClass(classId, true);
        requireMemberScope(scope.resolveActorByEmail(email), client, course);
        Reservation reservation = openReservation(classId, client);
        if (reservation == null) return Map.of("classId", classId, "cancelled", false);
        jdbc.update("UPDATE class_reservations SET status='CANCELADA',cancelled_at=? WHERE id=?", now(), reservation.id());
        notifyReservation(course, reservation, "CANCELADA");
        if ("RESERVADA".equals(reservation.status()) && course.startsAt().isAfter(now()) && "PROGRAMADA".equals(course.status())) {
            promoteNext(course);
        }
        return Map.of("classId", classId, "cancelled", true);
    }

    @Transactional(readOnly = true)
    public List<ReservationItem> roster(String email, UUID classId) {
        Account actor = scope.resolveActorByEmail(email);
        ClassRow course = loadClass(classId, false);
        scope.effectiveOrganizationId(actor, course.organizationId());
        scope.effectiveBranchId(actor, course.branchId());
        if (!scope.isCompanyWideRole(actor) && actor.getRoles().contains("ENTRENADOR")
                && !actor.getRoles().contains("ENTRENADOR_JEFE") && !actor.getRoles().contains("GERENTE_SEDE")
                && !actor.getRoles().contains("RECEPCION") && !actor.getId().equals(course.trainerId())) {
            throw new AccessDeniedException("Solo puedes consultar la lista de tus clases asignadas.");
        }
        return jdbc.query("""
                SELECT r.id, r.status, r.reserved_at, r.cancelled_at,
                       TRIM(CONCAT_WS(' ',c.first_name,c.last_name)) AS member_name
                FROM class_reservations r JOIN clients c ON c.id=r.client_id
                JOIN accounts a ON a.id=c.account_id
                WHERE r.class_id=? AND COALESCE(a.organization_id,?)=?
                ORDER BY r.reserved_at,r.id
                """, (rs, row) -> new ReservationItem(rs.getObject("id", UUID.class),
                rs.getString("member_name"), rs.getString("status"),
                lima(rs.getObject("reserved_at", LocalDateTime.class)),
                lima(rs.getObject("cancelled_at", LocalDateTime.class))),
                classId, TenantScopeResolver.DEFAULT_ORG_ID, course.organizationId());
    }

    @Transactional(readOnly = true)
    public List<ClassNotice> notices(String email) {
        Account actor = scope.resolveActorByEmail(email);
        boolean companyWide = scope.isCompanyWideRole(actor);
        boolean allBranchClasses = companyWide || actor.getRoles().contains("GERENTE_SEDE")
                || actor.getRoles().contains("RECEPCION");
        UUID branch = companyWide ? null : scope.effectiveBranchId(actor, null);
        return jdbc.query("""
                SELECT e.id,c.id AS class_id,c.title,b.name AS branch_name,e.event_type,e.created_at
                FROM class_reservation_events e
                JOIN class_reservations r ON r.id=e.reservation_id
                JOIN gym_classes c ON c.id=r.class_id
                JOIN branches b ON b.id=c.branch_id AND b.status='ACTIVO'
                WHERE b.organization_id=? AND (? OR b.id=?) AND (? OR c.trainer_account_id=?)
                AND e.created_at>CURRENT_TIMESTAMP-INTERVAL '30 days'
                ORDER BY e.created_at DESC,e.id DESC LIMIT 40
                """, (rs, row) -> new ClassNotice(rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class),
                rs.getString("title"), rs.getString("branch_name"), rs.getString("event_type"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).atZoneSameInstant(LIMA).toOffsetDateTime().toString()),
                scope.effectiveOrganizationId(actor, null), companyWide, branch, allBranchClasses, actor.getId());
    }

    private void promoteNext(ClassRow course) {
        var waiting = jdbc.query("""
                SELECT r.id,r.client_id,r.status FROM class_reservations r
                JOIN clients c ON c.id=r.client_id AND c.status='ACTIVO'
                JOIN accounts a ON a.id=c.account_id AND a.status='ACTIVO'
                WHERE r.class_id=? AND r.status='LISTA_ESPERA' AND COALESCE(a.organization_id,?)=?
                AND COALESCE(c.branch_id,a.primary_branch_id,
                    CASE WHEN COALESCE(a.organization_id,?)=? THEN CAST(? AS uuid) END)=?
                ORDER BY r.reserved_at,r.id
                """, ClassReservationsService::mapReservation, course.id(), TenantScopeResolver.DEFAULT_ORG_ID,
                course.organizationId(), TenantScopeResolver.DEFAULT_ORG_ID, TenantScopeResolver.DEFAULT_ORG_ID,
                TenantScopeResolver.DEFAULT_BRANCH_ID, course.branchId());
        waiting.stream().filter(item -> hasCurrentMembership(item.clientId())).findFirst().ifPresent(item -> {
            jdbc.update("UPDATE class_reservations SET status='RESERVADA' WHERE id=?", item.id());
            notifyReservation(course, item, "PROMOVIDA");
        });
    }

    private ClassRow loadClass(UUID id, boolean lock) {
        return jdbc.query("""
                SELECT c.id,b.organization_id,c.branch_id,c.trainer_account_id,c.title,c.starts_at,c.capacity,c.status
                FROM gym_classes c JOIN branches b ON b.id=c.branch_id AND b.status='ACTIVO' WHERE c.id=?
                """ + (lock ? " FOR UPDATE OF c" : ""), (rs, row) -> new ClassRow(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                rs.getString(5), rs.getObject(6, LocalDateTime.class), rs.getInt(7), rs.getString(8)), id)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Clase no encontrada."));
    }

    private Reservation openReservation(UUID classId, UUID client) {
        return jdbc.query("""
                SELECT id,client_id,status FROM class_reservations
                WHERE class_id=? AND client_id=? AND status IN ('RESERVADA','LISTA_ESPERA')
                """, ClassReservationsService::mapReservation, classId, client).stream().findFirst().orElse(null);
    }

    private static Reservation mapReservation(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Reservation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3));
    }

    private void requireMemberScope(Account actor, UUID clientId, ClassRow course) {
        scope.effectiveOrganizationId(actor, course.organizationId());
        if (!Objects.equals(scope.memberBranch(actor, members.registeredBranch(clientId)), course.branchId())) {
            throw new AccessDeniedException("La clase pertenece a otra sede.");
        }
    }

    private boolean hasCurrentMembership(UUID client) {
        return memberships.contract(client).map(c -> "VIGENTE".equalsIgnoreCase(c.status())
                && c.end() != null && c.end().isAfter(now().toLocalDate())).orElse(false);
    }

    private void notifyReservation(ClassRow course, Reservation reservation, String event) {
        String title = switch (event) {
            case "RESERVADA" -> "Reserva confirmada";
            case "PROMOVIDA" -> "Tu clase ya tiene cupo";
            case "CANCELADA" -> "Reserva cancelada";
            default -> "Estás en lista de espera";
        };
        String message = course.title() + " · " + course.startsAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO class_reservation_events(id,reservation_id,event_type,title,message)
                VALUES (?,?,?,?,?) ON CONFLICT(reservation_id,event_type) DO NOTHING
                """, id, reservation.id(), event, title, message);
        if (inserted > 0) jdbc.update("""
                INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,inbox_item_id)
                VALUES (?,?,'PUSH',?,'GYM VIP','Nuevo aviso disponible',?)
                ON CONFLICT(idempotency_key) DO NOTHING
                """, UUID.randomUUID(), "class_notice_" + id, reservation.clientId().toString(), id.toString());
    }

    private static Map<String, Object> reservationResult(Reservation item, UUID classId) {
        return Map.of("reservationId", item.id(), "classId", classId, "status", item.status());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), LIMA);
    }

    private static String lima(LocalDateTime date) {
        return date == null ? null : date.atZone(LIMA).toOffsetDateTime().toString();
    }
}
