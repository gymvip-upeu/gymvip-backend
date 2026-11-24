package com.gymvip.service;

import com.gymvip.dto.response.DashboardMetricsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardMetricsResponse getMetrics(boolean includeFinancials) {
        LocalDate todayLima = LocalDate.now(ZONE_LIMA);
        LocalDateTime todayStartUtc = todayLima.atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
        LocalDateTime tomorrowStartUtc = todayLima.plusDays(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
        LocalDateTime monthStartUtc = todayLima.withDayOfMonth(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();

        // 1. Resumen de contadores principales
        long totalClients = queryCount("SELECT count(*) FROM clients");
        long activePlans = queryCount("SELECT count(*) FROM plans WHERE status = 'ACTIVO'");
        long totalAlerts = queryCount(
                "SELECT count(*) FROM memberships WHERE (status = 'VIGENTE' AND end_date <= ?) OR status = 'VENCIDO'",
                todayLima.plusDays(7)
        );
        long todayAttendance = queryCount(
                "SELECT count(*) FROM member_attendance WHERE checked_at_utc >= ? AND checked_at_utc < ?",
                Timestamp.valueOf(todayStartUtc), Timestamp.valueOf(tomorrowStartUtc)
        );
        long pendingOrders = queryCount(
                "SELECT count(*) FROM supplement_orders WHERE payment_status = 'PENDIENTE' OR (payment_status = 'VERIFICADO' AND delivery_status != 'ENTREGADO')"
        );

        // 2. Estado de membresías (Donut)
        Map<String, Long> membershipsByStatus = new LinkedHashMap<>();
        membershipsByStatus.put("VIGENTE", 0L);
        membershipsByStatus.put("VENCIDO", 0L);
        membershipsByStatus.put("CONGELADO", 0L);
        membershipsByStatus.put("CANCELADO", 0L);
        try {
            jdbc.query("SELECT status, count(*) FROM memberships GROUP BY status", rs -> {
                String st = rs.getString(1);
                if (st != null) {
                    membershipsByStatus.put(st.toUpperCase(), rs.getLong(2));
                }
            });
        } catch (Exception ignored) {}

        // 3. Miembros por plan activo (Barras horizontales)
        List<DashboardMetricsResponse.PlanMetric> membershipsByPlan = new ArrayList<>();
        try {
            jdbc.query(
                    "SELECT coalesce(p.name, pv.name, 'Plan General') AS plan_name, count(m.id) AS cnt " +
                    "FROM memberships m " +
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
                    }
            );
        } catch (Exception ignored) {}

        // 4. Vencimientos por intervalo de tiempo
        Map<String, Long> expirationsByInterval = new LinkedHashMap<>();
        expirationsByInterval.put("Vencidas", queryCount("SELECT count(*) FROM memberships WHERE end_date < ?", todayLima));
        expirationsByInterval.put("Vence Hoy", queryCount("SELECT count(*) FROM memberships WHERE end_date = ?", todayLima));
        expirationsByInterval.put("1 a 7 días", queryCount("SELECT count(*) FROM memberships WHERE end_date > ? AND end_date <= ?", todayLima, todayLima.plusDays(7)));
        expirationsByInterval.put("8 a 30 días", queryCount("SELECT count(*) FROM memberships WHERE end_date > ? AND end_date <= ?", todayLima.plusDays(7), todayLima.plusDays(30)));
        expirationsByInterval.put("Más de 30 días", queryCount("SELECT count(*) FROM memberships WHERE end_date > ?", todayLima.plusDays(30)));

        // 5. Asistencia de los últimos 7 días (Evolución temporal)
        List<DashboardMetricsResponse.AttendanceDayMetric> recentAttendance = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = todayLima.minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay(ZONE_LIMA).withZoneSameInstant(ZONE_UTC).toLocalDateTime();
            long count = queryCount(
                    "SELECT count(*) FROM member_attendance WHERE checked_at_utc >= ? AND checked_at_utc < ?",
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
        try {
            jdbc.query("SELECT payment_status, count(*) FROM supplement_orders GROUP BY payment_status", rs -> {
                String st = rs.getString(1);
                if (st != null) {
                    ordersByStatus.put(st.toUpperCase(), rs.getLong(2));
                }
            });
        } catch (Exception ignored) {}

        // 7. Resumen Financiero (Exclusivo ADMIN y AUDITOR, null para RECEPCION)
        DashboardMetricsResponse.FinancialMetric financialSummary = null;
        if (includeFinancials) {
            BigDecimal monthlyRevenue = querySum(
                    "SELECT coalesce(sum(amount_pen), 0) FROM payment_records WHERE payment_timestamp_utc >= ?",
                    Timestamp.valueOf(monthStartUtc)
            );
            BigDecimal todayRevenue = querySum(
                    "SELECT coalesce(sum(amount_pen), 0) FROM payment_records WHERE payment_timestamp_utc >= ? AND payment_timestamp_utc < ?",
                    Timestamp.valueOf(todayStartUtc), Timestamp.valueOf(tomorrowStartUtc)
            );

            Map<String, BigDecimal> revenueByMethod = new LinkedHashMap<>();
            try {
                jdbc.query(
                        "SELECT payment_method, coalesce(sum(amount_pen), 0) FROM payment_records " +
                        "WHERE payment_timestamp_utc >= ? GROUP BY payment_method",
                        rs -> {
                            String method = rs.getString(1);
                            if (method != null) {
                                revenueByMethod.put(method.toUpperCase(), rs.getBigDecimal(2));
                            }
                        },
                        Timestamp.valueOf(monthStartUtc)
                );
            } catch (Exception ignored) {}

            financialSummary = new DashboardMetricsResponse.FinancialMetric(
                    monthlyRevenue,
                    todayRevenue,
                    revenueByMethod
            );
        }

        return new DashboardMetricsResponse(
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
    }

    private long queryCount(String sql, Object... args) {
        try {
            Long val = jdbc.queryForObject(sql, Long.class, args);
            return val == null ? 0L : val;
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal querySum(String sql, Object... args) {
        try {
            BigDecimal val = jdbc.queryForObject(sql, BigDecimal.class, args);
            return val == null ? BigDecimal.ZERO : val;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
