package com.gymvip.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;

/** Stop before Flyway can write into a shared schema, including accidental URL overrides. */
@Configuration
public class IsolatedDatabaseTestConfig {
    @Bean
    FlywayMigrationStrategy isolatedTestMigration(Environment env, DataSource source) {
        return flyway -> {
            String schema = env.getProperty("TEST_DB_SCHEMA", "gymvip_test_alexander");
            if (!"gymvip_test_alexander".equals(schema) ||
                    !schema.equals(flyway.getConfiguration().getDefaultSchema()) ||
                    !schema.equals(env.getProperty("spring.datasource.hikari.schema")) ||
                    !schema.equals(env.getProperty("spring.jpa.properties.hibernate.default_schema"))) {
                throw new IllegalStateException("Pruebas rechazadas: datasource/Hibernate/Flyway deben usar gymvip_test_alexander.");
            }
            String url = env.getProperty("spring.datasource.url", "");
            if (url.toLowerCase(java.util.Locale.ROOT).contains("currentschema="))
                throw new IllegalStateException("Pruebas rechazadas: no use currentSchema en la URL JDBC.");
            flyway.migrate();
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT current_schema()")) {
                if (!result.next() || !schema.equals(result.getString(1)))
                    throw new IllegalStateException("El esquema real de pruebas no coincide con el permitido.");
            } catch (java.sql.SQLException e) { throw new IllegalStateException("No se pudo verificar el esquema aislado.", e); }
        };
    }
}
