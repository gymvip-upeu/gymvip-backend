package com.gymvip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@SpringBootTest
public class DatabaseConnectivityCheckTest {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Test
    @DisplayName("Inspeccionar servidor PostgreSQL real y estado inicial de esquemas en Render")
    void checkDatabaseConnectionAndTables() throws Exception {
        System.out.println("=== DIAGNOSTICO DE CONEXION POSTGRESQL RENDER ===");
        System.out.println("URL configurada (sin credenciales): " + dbUrl.replaceAll("//[^@]+@", "//***@"));

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SHOW server_version;")) {
                if (rs.next()) {
                    System.out.println("PostgreSQL Server Version: " + rs.getString(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, current_schema();")) {
                if (rs.next()) {
                    System.out.println("Database: " + rs.getString(1));
                    System.out.println("User: " + rs.getString(2));
                    System.out.println("Default Schema: " + rs.getString(3));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT table_schema, table_name FROM information_schema.tables " +
                    "WHERE table_schema IN ('public', 'gymvip_test') ORDER BY table_schema, table_name;")) {
                System.out.println("Tablas encontradas en la base:");
                boolean hasTables = false;
                while (rs.next()) {
                    hasTables = true;
                    System.out.println(" - " + rs.getString(1) + "." + rs.getString(2));
                }
                if (!hasTables) {
                    System.out.println(" (Ninguna tabla encontrada en public ni gymvip_test: Base recien creada / vacia)");
                }
            }
        }
        System.out.println("=== FIN DIAGNOSTICO ===");
    }
}
