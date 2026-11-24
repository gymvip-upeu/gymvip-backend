package com.gymvip.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO agregado para el panel de control ejecutivo y operativo (Dashboard Luxury Dark & Gold).
 * Diseñado para consultas analíticas de alto rendimiento sin sobrecarga N+1 en frontend.
 */
public record DashboardMetricsResponse(
        long totalClients,
        long activePlans,
        long totalAlerts,
        long todayAttendance,
        long pendingOrders,
        Map<String, Long> membershipsByStatus,
        List<PlanMetric> membershipsByPlan,
        Map<String, Long> expirationsByInterval,
        List<AttendanceDayMetric> recentAttendance7Days,
        Map<String, Long> ordersByStatus,
        FinancialMetric financialSummary
) {
    public record PlanMetric(String planName, long memberCount) {}
    public record AttendanceDayMetric(String date, String dayName, long count) {}
    public record FinancialMetric(
            BigDecimal monthlyRevenuePen,
            BigDecimal todayRevenuePen,
            Map<String, BigDecimal> revenueByMethod
    ) {}
}
