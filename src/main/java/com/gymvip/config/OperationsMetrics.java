package com.gymvip.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "gymvip.operations-metrics-enabled", havingValue = "true")
public class OperationsMetrics {
    @Bean
    MeterBinder gymVipOperations(JdbcTemplate jdbc) {
        return registry -> {
            Gauge.builder("gymvip.push.oldest.pending.seconds", jdbc, db -> number(db, """
                    SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(created_at_utc))), 0)
                    FROM notification_outbox WHERE channel='PUSH' AND status='PENDING'
                    """)).description("Age of the oldest push pending dispatch").register(registry);
            Gauge.builder("gymvip.payments.review.pending", jdbc, db -> number(db, """
                    SELECT (SELECT COUNT(*) FROM supplement_payment_links WHERE status='REVIEW')
                         + (SELECT COUNT(*) FROM membership_payment_links WHERE status='REVIEW')
                    """)).description("Culqi payments requiring reconciliation").register(registry);
            Gauge.builder("gymvip.database.reachable", jdbc, db -> number(db, "SELECT 1"))
                    .description("PostgreSQL responds to a query").register(registry);
        };
    }

    private static double number(JdbcTemplate jdbc, String sql) {
        try {
            Number value = jdbc.queryForObject(sql, Number.class);
            return value == null ? Double.NaN : value.doubleValue();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }
}
