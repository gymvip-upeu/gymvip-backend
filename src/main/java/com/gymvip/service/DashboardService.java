package com.gymvip.service;

import com.gymvip.dto.response.DashboardMetricsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gymvip.security.TenantScopeResolver.OperationalScope;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");
    public static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    private final JdbcTemplate jdbc;
    private static final String SCOPED_TABLES = """
            WITH scoped_branches AS (
                SELECT id FROM branches WHERE organization_id=? AND (?::uuid IS NULL OR id=?)
            ), scoped_clients AS (
                SELECT c.* FROM clients c JOIN scoped_branches b ON b.id=c.branch_id
            ), scoped_memberships AS (
                SELECT m.* FROM memberships m JOIN scoped_clients c ON c.id=m.client_id
            ), scoped_attendance AS (
                SELECT a.* FROM member_attendance a JOIN scoped_clients c ON c.id=a.client_id
            ), scoped_orders AS (
                SELECT o.* FROM supplement_orders o JOIN scoped_branches b ON b.id=o.branch_id
            ), scoped_payments AS (
                SELECT r.* FROM payment_records r JOIN scoped_memberships m ON m.id=r.membership_id
            )
            """;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardMetricsResponse getMetrics(boolean includeFinancials, OperationalScope scope) {
        LocalDate todayLima = LocalDate.now(ZONE_LIMA);
        LocalDateTime todayStartUtc = todayLima.atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
        LocalDateTime tomorrowStartUtc = todayLima.plusDays(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
        LocalDateTime monthStartUtc = todayLima.withDayOfMonth(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();

        // 1. Resumen de contadores principales
        long totalClients = queryCount(scope, "SELECT count(*) FROM scoped_clients");
        long activePlans = queryCount(scope, "SELECT count(DISTINCT p.id) FROM plans p JOIN plan_versions v ON v.plan_id=p.id JOIN scoped_memberships m ON m.plan_version_id=v.id WHERE p.status='ACTIVO'");
        long totalAlerts = queryCount(scope, 
                "SELECT count(*) FROM scoped_memberships WHERE (status = 'VIGENTE' AND end_date <= ?) OR status = 'VENCIDO'",
                todayLima.plusDays(7)
        );
        long todayAttendance = queryCount(scope, 
                "SELECT count(*) FROM scoped_attendance WHERE checked_at_utc >= ? AND checked_at_utc < ?",
                Timestamp.valueOf(todayStartUtc), Timestamp.valueOf(tomorrowStartUtc)
        );
        long pendingOrders = queryCount(scope, 
                "SELECT count(*) FROM scoped_orders WHERE payment_status = 'PENDIENTE' OR (payment_status = 'VERIFICADO' AND fulfillment_status != 'ENTREGADO')"
        );

        // 2. Estado de membresías (Donut)
        Map<String, Long> membershipsByStatus = new LinkedHashMap<>();
        membershipsByStatus.put("VIGENTE", 0L);
        membershipsByStatus.put("VENCIDO", 0L);
        membershipsByStatus.put("CONGELADO", 0L);
        membershipsByStatus.put("CANCELADO", 0L);
        jdbc.query(SCOPED_TABLES + "SELECT status, count(*) FROM scoped_memberships GROUP BY status", rs -> {
            String st = rs.getString(1);
            if (st != null) {
                membershipsByStatus.put(st.toUpperCase(), rs.getLong(2));
            }
        }, scope.parameters());

        // 3. Miembros por plan activo (Barras horizontales)
        List<DashboardMetricsResponse.PlanMetric> membershipsByPlan = new ArrayList<>();
        jdbc.query(SCOPED_TABLES +
                "SELECT coalesce(p.name, pv.name, 'Plan General') AS plan_name, count(m.id) AS cnt " +
                "FROM scoped_memberships m " +
                "JOIN plan_versions pv ON m.plan_version_id = pv.id " +
                "LEFT JOIN plans p ON pv.plan_id = p.id " +
                "WHERE m.status = 'VIGENTE' " +
                "GROUP BY coalesce(p.name, pv.name, 'Plan General') " +
                "ORDER BY cnt DESC LIMIT 6",
                rs -> {
                    membershipsByPlan.add(new DashboardMetricsResponse.PlanMetric(
                            rs.getString("plan_name"),
                            rs.getLong("cnt")
                    ));
                }, scope.parameters()
        );

        // 4. Vencimientos por intervalo de tiempo
        Map<String, Long> expirationsByInterval = new LinkedHashMap<>();
        expirationsByInterval.put("Vencidas", queryCount(scope, "SELECT count(*) FROM scoped_memberships WHERE end_date < ?", todayLima));
        expirationsByInterval.put("Vence Hoy", queryCount(scope, "SELECT count(*) FROM scoped_memberships WHERE end_date = ?", todayLima));
        expirationsByInterval.put("1 a 7 días", queryCount(scope, "SELECT count(*) FROM scoped_memberships WHERE end_date > ? AND end_date <= ?", todayLima, todayLima.plusDays(7)));
        expirationsByInterval.put("8 a 30 días", queryCount(scope, "SELECT count(*) FROM scoped_memberships WHERE end_date > ? AND end_date <= ?", todayLima.plusDays(7), todayLima.plusDays(30)));
        expirationsByInterval.put("Más de 30 días", queryCount(scope, "SELECT count(*) FROM scoped_memberships WHERE end_date > ?", todayLima.plusDays(30)));

        // 5. Asistencia de los últimos 7 días (Evolución temporal)
        List<DashboardMetricsResponse.AttendanceDayMetric> recentAttendance = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = todayLima.minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
            long count = queryCount(scope, 
                    "SELECT count(*) FROM scoped_attendance WHERE checked_at_utc >= ? AND checked_at_utc < ?",
                    Timestamp.valueOf(dayStart), Timestamp.valueOf(dayEnd)
            );
            String dayName = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("es", "PE"));
            recentAttendance.add(new DashboardMetricsResponse.AttendanceDayMetric(
                    day.toString(),
                    dayName.substring(0, 1).toUpperCase() + dayName.substring(1),
                    count
            ));
        }

        // 6. Pedidos por estado
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        ordersByStatus.put("PENDIENTE", 0L);
        ordersByStatus.put("VERIFICADO", 0L);
        ordersByStatus.put("ENTREGADO", 0L);
        ordersByStatus.put("CANCELADO", 0L);
        jdbc.query(SCOPED_TABLES + "SELECT payment_status, count(*) FROM scoped_orders GROUP BY payment_status", rs -> {
            String st = rs.getString(1);
            if (st != null) {
                ordersByStatus.put(st.toUpperCase(), rs.getLong(2));
            }
        }, scope.parameters());

        // 7. Resumen Financiero (Exclusivo ADMIN y AUDITOR, null para RECEPCION)
        DashboardMetricsResponse.FinancialMetric financialSummary = null;
        if (includeFinancials) {
            BigDecimal monthlyRevenue = querySum(scope, 
                    "SELECT coalesce(sum(amount_pen), 0) FROM scoped_payments WHERE payment_timestamp_utc >= ?",
                    Timestamp.valueOf(monthStartUtc)
            );
            BigDecimal todayRevenue = querySum(scope, 
                    "SELECT coalesce(sum(amount_pen), 0) FROM scoped_payments WHERE payment_timestamp_utc >= ? AND payment_timestamp_utc < ?",
                    Timestamp.valueOf(todayStartUtc), Timestamp.valueOf(tomorrowStartUtc)
            );

            Map<String, BigDecimal> revenueByMethod = new LinkedHashMap<>();
            jdbc.query(SCOPED_TABLES +
                    "SELECT payment_method, coalesce(sum(amount_pen), 0) FROM scoped_payments " +
                    "WHERE payment_timestamp_utc >= ? GROUP BY payment_method",
                    rs -> {
                        String method = rs.getString(1);
                        if (method != null) {
                            revenueByMethod.put(method.toUpperCase(), rs.getBigDecimal(2));
                        }
                    },
                    scope.parameters(Timestamp.valueOf(monthStartUtc))
            );

            financialSummary = new DashboardMetricsResponse.FinancialMetric(
                    monthlyRevenue,
                    todayRevenue,
                    revenueByMethod
            );
        }

        DashboardMetricsResponse response = new DashboardMetricsResponse(
                totalClients,
                activePlans,
                totalAlerts,
                todayAttendance,
                pendingOrders,
                membershipsByStatus,
                membershipsByPlan,
                expirationsByInterval,
                recentAttendance,
                ordersByStatus,
                financialSummary
        );

        return response;
    }

    private long queryCount(OperationalScope scope, String sql, Object... args) {
        Long val = jdbc.queryForObject(SCOPED_TABLES + sql, Long.class, scope.parameters(args));
        return val == null ? 0L : val;
    }

    private BigDecimal querySum(OperationalScope scope, String sql, Object... args) {
        BigDecimal val = jdbc.queryForObject(SCOPED_TABLES + sql, BigDecimal.class, scope.parameters(args));
        return val == null ? BigDecimal.ZERO : val;
    }
}
