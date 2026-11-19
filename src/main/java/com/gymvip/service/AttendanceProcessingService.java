package com.gymvip.service;

import com.gymvip.dto.attendance.AttendanceDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class AttendanceProcessingService {

    private final JdbcClient jdbc;
    private final CoverageService coverage;
    private final MemberWorkspaceService workspace;
    private final AuditService audit;
    private final Clock clock;

    public AttendanceProcessingService(
            JdbcClient jdbc,
            CoverageService coverage,
            MemberWorkspaceService workspace,
            AuditService audit,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.coverage = coverage;
        this.workspace = workspace;
        this.audit = audit;
        this.clock = clock;
    }

    public MemberCheckInResponse processMemberSelfCheckIn(
            String memberIdentifier,
            String challengeToken,
            UUID requestId,
            String ipAddress
    ) {
        Instant now = clock.instant();
        ZoneId limaZone = ZoneId.of("America/Lima");
        LocalDate today = LocalDate.now(clock.withZone(limaZone));

        // 1. Identificar al socio autenticado
        UUID clientId;
        try {
            clientId = workspace.ownClient(memberIdentifier);
        } catch (Exception e) {
            recordRejection(null, null, today, null, "UNAUTHORIZED_SESSION", "No se encontró ficha de socio activa asociada a la cuenta.", ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("UNAUTHORIZED_SESSION", "No cuenta con acceso de socio activo en el sistema.", "Usuario", "");
        }

        var clientDataOpt = jdbc.sql("SELECT id, first_name, last_name, member_code, account_id, status FROM clients WHERE id = :id")
                .param("id", clientId)
                .query((rs, rowNum) -> Map.of(
                        "name", rs.getString("first_name") + " " + rs.getString("last_name"),
                        "code", rs.getString("member_code") != null ? rs.getString("member_code") : "",
                        "accountId", rs.getObject("account_id", UUID.class) != null ? rs.getObject("account_id", UUID.class) : UUID.randomUUID(),
                        "status", rs.getString("status") != null ? rs.getString("status") : "ACTIVO"
                ))
                .optional();

        if (clientDataOpt.isEmpty()) {
            recordRejection(clientId, null, today, null, "UNAUTHORIZED_SESSION", "No se encontró ficha de socio activa.", ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("UNAUTHORIZED_SESSION", "No cuenta con acceso de socio activo en el sistema.", "Usuario", "");
        }
        var clientData = clientDataOpt.get();

        String memberName = (String) clientData.get("name");
        String memberCode = (String) clientData.get("code");
        UUID accountId = (UUID) clientData.get("accountId");
        String clientStatus = (String) clientData.get("status");

        if (!"ACTIVO".equalsIgnoreCase(clientStatus)) {
            recordRejection(clientId, accountId, today, null, "MEMBER_INACTIVE", "Ficha de socio inhabilitada o suspendida.", ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("MEMBER_INACTIVE", "Su cuenta de socio se encuentra inactiva.", memberName, memberCode);
        }

        // 2. Validar Desafío QR
        String cleanToken = challengeToken != null ? challengeToken.trim() : "";
        var challengeRow = jdbc.sql("""
                SELECT c.id, c.terminal_id, c.expires_at_utc, t.name AS terminal_name, t.status AS terminal_status
                FROM attendance_terminal_challenges c
                JOIN attendance_terminals t ON t.id = c.terminal_id
                WHERE c.challenge_token = :token
                """)
                .param("token", cleanToken)
                .query((rs, rowNum) -> Map.of(
                        "terminalId", rs.getObject("terminal_id", UUID.class),
                        "terminalName", rs.getString("terminal_name"),
                        "terminalStatus", rs.getString("terminal_status"),
                        "expiresAt", rs.getTimestamp("expires_at_utc").toInstant()
                ))
                .optional();

        if (challengeRow.isEmpty()) {
            recordRejection(clientId, accountId, today, null, "CHALLENGE_INVALID", "Token QR de recepción no reconocido.", ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("CHALLENGE_INVALID", "El código QR escaneado es inválido o no corresponde a una terminal autorizada.", memberName, memberCode);
        }

        UUID terminalId = (UUID) challengeRow.get().get("terminalId");
        String terminalName = (String) challengeRow.get().get("terminalName");
        String terminalStatus = (String) challengeRow.get().get("terminalStatus");
        Instant expiresAt = (Instant) challengeRow.get().get("expiresAt");

        if (!"ACTIVA".equalsIgnoreCase(terminalStatus)) {
            recordRejection(clientId, accountId, today, terminalId, "TERMINAL_INACTIVE", "La terminal " + terminalName + " no está activa.", ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("TERMINAL_INACTIVE", "La terminal de recepción se encuentra actualmente inactiva.", memberName, memberCode);
        }

        if (now.isAfter(expiresAt)) {
            recordRejection(clientId, accountId, today, terminalId, "CHALLENGE_EXPIRED", "Token QR expirado a las " + expiresAt, ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected("CHALLENGE_EXPIRED", "El código QR ha expirado. Apunte nuevamente a la pantalla de recepción para leer el código renovado.", memberName, memberCode);
        }

        // 3. Validar vigencia de membresía (Coverage)
        var verified = coverage.verify(clientId);
        if (!verified.allowed()) {
            String state = verified.state() != null ? verified.state() : "SIN_MEMBRESIA";
            String reasonCode = switch (state) {
                case "VENCIDO" -> "MEMBERSHIP_EXPIRED";
                case "CANCELADA" -> "MEMBERSHIP_CANCELLED";
                default -> "MEMBERSHIP_NOT_STARTED";
            };
            String userMsg = "Acceso no autorizado: " + verified.reason();
            recordRejection(clientId, accountId, today, terminalId, reasonCode, verified.reason(), ipAddress, memberIdentifier);
            return MemberCheckInResponse.rejected(reasonCode, userMsg, memberName, memberCode);
        }

        // 4. Registro atómico e idempotente de asistencia
        UUID attendanceId = UUID.randomUUID();
        String auditRef = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        int inserted = jdbc.sql("""
                INSERT INTO member_attendance (
                    id, client_id, attendance_date, checked_at_utc, recorded_by, terminal_id, check_in_method, request_id, audit_ref
                ) VALUES (
                    :id, :clientId, :day, :now, :actor, :terminalId, 'MEMBER_SCANNED_RECEPTION_QR', :reqId, :auditRef
                ) ON CONFLICT (client_id, attendance_date) DO NOTHING
                """)
                .param("id", attendanceId)
                .param("clientId", clientId)
                .param("day", today)
                .param("now", java.sql.Timestamp.from(now))
                .param("actor", memberIdentifier)
                .param("terminalId", terminalId)
                .param("reqId", requestId)
                .param("auditRef", auditRef)
                .update();

        if (inserted == 1) {
            audit.recordEvent(memberIdentifier, "MEMBER_SELF_CHECK_IN", "Client", clientId.toString(),
                    "Asistencia autoservicio en " + terminalName + " (" + today + ")");
            return MemberCheckInResponse.recorded(today, now, memberName, memberCode);
        } else {
            // Ya existe registro el día de hoy
            return MemberCheckInResponse.alreadyRegistered(today, now, memberName, memberCode);
        }
    }

    private void recordRejection(
            UUID clientId,
            UUID accountId,
            LocalDate date,
            UUID terminalId,
            String reason,
            String details,
            String ip,
            String recordedBy
    ) {
        jdbc.sql("""
                INSERT INTO attendance_rejection_logs (
                    id, client_id, account_id, attempted_at_utc, attendance_date, terminal_id, rejection_reason, details, ip_address, recorded_by
                ) VALUES (
                    :id, :clientId, :accountId, :now, :date, :terminalId, :reason, :details, :ip, :recordedBy
                )
                """)
                .param("id", UUID.randomUUID())
                .param("clientId", clientId)
                .param("accountId", accountId)
                .param("now", java.sql.Timestamp.from(clock.instant()))
                .param("date", date)
                .param("terminalId", terminalId)
                .param("reason", reason)
                .param("details", details != null ? details.substring(0, Math.min(details.length(), 500)) : null)
                .param("ip", ip)
                .param("recordedBy", recordedBy)
                .update();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentFeed(int limit) {
        int safeLimit = Math.clamp(limit, 1, 50);
        return jdbc.sql("""
                SELECT a.id, a.client_id, c.first_name, c.last_name, c.member_code,
                       a.attendance_date, a.checked_at_utc, a.check_in_method, a.recorded_by,
                       t.name AS terminal_name, 'APROBADO' AS status
                FROM member_attendance a
                JOIN clients c ON c.id = a.client_id
                LEFT JOIN attendance_terminals t ON t.id = a.terminal_id
                ORDER BY a.checked_at_utc DESC
                LIMIT :lim
                """)
                .param("lim", safeLimit)
                .query((rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getObject("id", UUID.class));
                    map.put("clientId", rs.getObject("client_id", UUID.class));
                    map.put("memberName", rs.getString("first_name") + " " + rs.getString("last_name"));
                    map.put("memberCode", rs.getString("member_code"));
                    map.put("attendanceDate", rs.getDate("attendance_date").toLocalDate().toString());
                    map.put("checkedAtUtc", rs.getTimestamp("checked_at_utc").toInstant().toString());
                    map.put("checkInMethod", rs.getString("check_in_method"));
                    map.put("recordedBy", rs.getString("recorded_by"));
                    map.put("terminalName", rs.getString("terminal_name") != null ? rs.getString("terminal_name") : "Mostrador General");
                    map.put("status", "APROBADO");
                    return map;
                })
                .list();
    }

    @Transactional(readOnly = true)
    public AgendaPageResponse queryAttendanceAgenda(
            LocalDate from,
            LocalDate to,
            String query,
            String method,
            String statusFilter,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        int offset = safePage * safeSize;

        ZoneId limaZone = ZoneId.of("America/Lima");
        LocalDate today = LocalDate.now(clock.withZone(limaZone));
        LocalDate defaultFrom = from != null ? from : today.minusDays(7);
        LocalDate defaultTo = to != null ? to : today;

        String queryParam = query != null ? "%" + query.trim().toLowerCase() + "%" : "%";

        // Query aprobados
        boolean includeAprobados = statusFilter == null || "TODOS".equalsIgnoreCase(statusFilter)
                || "APROBADO".equalsIgnoreCase(statusFilter) || "ATTENDANCE_RECORDED".equalsIgnoreCase(statusFilter);
        boolean includeRechazados = statusFilter == null || "TODOS".equalsIgnoreCase(statusFilter)
                || "RECHAZADO".equalsIgnoreCase(statusFilter) || "ALREADY_REGISTERED".equalsIgnoreCase(statusFilter)
                || "COVERAGE_EXPIRED".equalsIgnoreCase(statusFilter);

        List<AgendaItemDto> items = new ArrayList<>();

        if (includeAprobados) {
            String methodFilter = (method != null && !"TODOS".equalsIgnoreCase(method)) ? method : "%";
            var aprobados = jdbc.sql("""
                    SELECT a.id, a.client_id, c.first_name, c.last_name, c.member_code, c.document_number,
                           a.attendance_date, a.checked_at_utc, a.check_in_method, a.recorded_by,
                           a.terminal_id, t.name AS terminal_name, 'ATTENDANCE_RECORDED' AS status, 'Ingreso autorizado' AS details
                    FROM member_attendance a
                    JOIN clients c ON c.id = a.client_id
                    LEFT JOIN attendance_terminals t ON t.id = a.terminal_id
                    WHERE a.attendance_date BETWEEN :from AND :to
                      AND (lower(c.first_name) LIKE :q OR lower(c.last_name) LIKE :q OR lower(c.member_code) LIKE :q OR lower(c.document_number) LIKE :q)
                      AND (CAST(:method AS varchar) = '%' OR a.check_in_method = :method)
                    ORDER BY a.checked_at_utc DESC
                    LIMIT :lim OFFSET :off
                    """)
                    .param("from", defaultFrom)
                    .param("to", defaultTo)
                    .param("q", queryParam)
                    .param("method", methodFilter)
                    .param("lim", safeSize)
                    .param("off", offset)
                    .query((rs, rowNum) -> new AgendaItemDto(
                            rs.getObject("id", UUID.class),
                            rs.getObject("client_id", UUID.class),
                            rs.getString("member_code"),
                            rs.getString("first_name") + " " + rs.getString("last_name"),
                            maskDni(rs.getString("document_number")),
                            rs.getDate("attendance_date").toLocalDate(),
                            rs.getTimestamp("checked_at_utc").toInstant(),
                            rs.getString("check_in_method"),
                            rs.getString("recorded_by"),
                            rs.getObject("terminal_id", UUID.class),
                            rs.getString("terminal_name") != null ? rs.getString("terminal_name") : "Mostrador General",
                            rs.getString("status"),
                            rs.getString("details")
                    ))
                    .list();
            items.addAll(aprobados);
        }

        if (includeRechazados && items.size() < safeSize) {
            int remaining = safeSize - items.size();
            var rechazados = jdbc.sql("""
                    SELECT r.id, r.client_id, c.first_name, c.last_name, c.member_code, c.document_number,
                           r.attendance_date, r.attempted_at_utc AS checked_at_utc,
                           'RECHAZO' AS check_in_method, r.recorded_by, r.terminal_id,
                           t.name AS terminal_name,
                           CASE
                               WHEN r.rejection_reason = 'ALREADY_REGISTERED' THEN 'ALREADY_REGISTERED'
                               WHEN r.rejection_reason = 'COVERAGE_EXPIRED' THEN 'COVERAGE_EXPIRED'
                               ELSE 'RECHAZADO'
                           END AS status,
                           concat(r.rejection_reason, ': ', coalesce(r.details, '')) AS details
                    FROM attendance_rejection_logs r
                    LEFT JOIN clients c ON c.id = r.client_id
                    LEFT JOIN attendance_terminals t ON t.id = r.terminal_id
                    WHERE r.attendance_date BETWEEN :from AND :to
                      AND (CAST(:q AS varchar) = '%' OR lower(coalesce(c.first_name, '')) LIKE :q OR lower(coalesce(c.last_name, '')) LIKE :q OR lower(coalesce(c.member_code, '')) LIKE :q)
                    ORDER BY r.attempted_at_utc DESC
                    LIMIT :lim
                    """)
                    .param("from", defaultFrom)
                    .param("to", defaultTo)
                    .param("q", queryParam)
                    .param("lim", remaining)
                    .query((rs, rowNum) -> new AgendaItemDto(
                            rs.getObject("id", UUID.class),
                            rs.getObject("client_id", UUID.class),
                            rs.getString("member_code") != null ? rs.getString("member_code") : "NO_IDENTIFICADO",
                            rs.getString("first_name") != null ? (rs.getString("first_name") + " " + rs.getString("last_name")) : "Desconocido",
                            maskDni(rs.getString("document_number")),
                            rs.getDate("attendance_date").toLocalDate(),
                            rs.getTimestamp("checked_at_utc").toInstant(),
                            "INTENTO_FALLIDO",
                            rs.getString("recorded_by"),
                            rs.getObject("terminal_id", UUID.class),
                            rs.getString("terminal_name") != null ? rs.getString("terminal_name") : "Terminal",
                            rs.getString("status"),
                            rs.getString("details")
                    ))
                    .list();
            items.addAll(rechazados);
        }

        // Estadísticas de dashboard
        long totalIngresosHoy = jdbc.sql("SELECT count(*) FROM member_attendance WHERE attendance_date = :today")
                .param("today", today)
                .query(Long.class)
                .single();

        long totalRechazosHoy = jdbc.sql("SELECT count(*) FROM attendance_rejection_logs WHERE attendance_date = :today")
                .param("today", today)
                .query(Long.class)
                .single();

        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        long totalIngresosMes = jdbc.sql("SELECT count(*) FROM member_attendance WHERE attendance_date BETWEEN :firstDay AND :today")
                .param("firstDay", firstDayOfMonth)
                .param("today", today)
                .query(Long.class)
                .single();

        long totalElements = jdbc.sql("SELECT count(*) FROM member_attendance WHERE attendance_date BETWEEN :from AND :to")
                .param("from", defaultFrom)
                .param("to", defaultTo)
                .query(Long.class)
                .single();

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        return new AgendaPageResponse(
                items,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                new AgendaStats(totalIngresosHoy, totalRechazosHoy, totalIngresosMes)
        );
    }

    @Transactional(readOnly = true)
    public String exportAttendanceCsv(LocalDate from, LocalDate to, String query, String method, String status) {
        var page = queryAttendanceAgenda(from, to, query, method, status, 0, 500);
        StringWriter writer = new StringWriter();
        writer.write("Fecha,Hora_UTC,Socio,Codigo_Socio,DNI_Enmascarado,Metodo,Terminal,Estado,Detalles\n");

        for (var item : page.content()) {
            writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    sanitizeCsv(item.attendanceDate().toString()),
                    sanitizeCsv(item.checkedAtUtc() != null ? item.checkedAtUtc().toString() : ""),
                    sanitizeCsv(item.memberName()),
                    sanitizeCsv(item.memberCode()),
                    sanitizeCsv(item.documentNumber() != null ? item.documentNumber() : ""),
                    sanitizeCsv(item.checkInMethod()),
                    sanitizeCsv(item.terminalName()),
                    sanitizeCsv(item.status()),
                    sanitizeCsv(item.details())
            ));
        }

        return writer.toString();
    }

    private static String maskDni(String doc) {
        if (doc == null || doc.length() < 4) return "****";
        return doc.substring(0, 2) + "****" + doc.substring(doc.length() - 2);
    }

    private static String sanitizeCsv(String val) {
        if (val == null) return "";
        String s = val.replace("\"", "\"\"");
        // Prevenir inyección de fórmulas de hojas de cálculo (=, +, -, @)
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            s = "'" + s;
        }
        return s;
    }
}
