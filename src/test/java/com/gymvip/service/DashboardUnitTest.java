package com.gymvip.service;

import com.gymvip.controller.DashboardController;
import com.gymvip.dto.response.DashboardMetricsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias de Dashboard y Métricas Analíticas")
class DashboardUnitTest {
    final com.gymvip.security.TenantScopeResolver.OperationalScope scope = new com.gymvip.security.TenantScopeResolver.OperationalScope(java.util.UUID.randomUUID(), null);

    @Test
    @DisplayName("Admin y Auditor reciben métricas completas incluyendo resumen financiero")
    void adminReceivesCompleteMetricsWithFinancials() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(10L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(25L);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class))).thenReturn(new BigDecimal("1500.50"));

        DashboardService service = new DashboardService(jdbc);
        DashboardMetricsResponse metrics = service.getMetrics(true, scope);

        assertNotNull(metrics);
        assertNotNull(metrics.financialSummary(), "El resumen financiero no debe ser nulo para administradores");
        assertEquals(new BigDecimal("1500.50"), metrics.financialSummary().monthlyRevenuePen());
        assertNotNull(metrics.expirationsByInterval());
        assertNotNull(metrics.recentAttendance7Days());
        assertEquals(7, metrics.recentAttendance7Days().size(), "Debe contener exactamente 7 días de historial");
    }

    @Test
    @DisplayName("Recepción recibe métricas operativas pero resumen financiero es estrictamente nulo")
    void recepcionReceivesOperationalMetricsWithoutFinancials() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);

        DashboardService service = new DashboardService(jdbc);
        DashboardMetricsResponse metrics = service.getMetrics(false, scope);

        assertNotNull(metrics);
        assertNull(metrics.financialSummary(), "El resumen financiero DEBE ser nulo para roles sin privilegios contables");
        assertTrue(metrics.totalClients() >= 0);
        assertTrue(metrics.activePlans() >= 0);
    }

    @Test
    @DisplayName("DashboardController delega correctamente según el rol del usuario autenticado")
    void dashboardControllerEnforcesRolePrivilege() {
        DashboardService service = Mockito.mock(DashboardService.class);
        DashboardMetricsResponse mockResp = new DashboardMetricsResponse(
                5, 4, 0, 0, 0,
                java.util.Map.of("VIGENTE", 5L),
                List.of(),
                java.util.Map.of(),
                List.of(),
                java.util.Map.of(),
                null
        );
        when(service.getMetrics(anyBoolean(), any())).thenReturn(mockResp);

        var resolver = Mockito.mock(com.gymvip.security.TenantScopeResolver.class);
        when(resolver.operationalScope(any(), any(), any())).thenReturn(scope);
        DashboardController controller = new DashboardController(service, resolver);

        // Caso 1: Recepcionista
        var authRecepcion = new UsernamePasswordAuthenticationToken(
                "recepcion@vipcenterfit.pe", "pwd", List.of(new SimpleGrantedAuthority("ROLE_RECEPCION"))
        );
        controller.getMetrics(authRecepcion, null, null);
        Mockito.verify(service).getMetrics(false, scope);

        // Caso 2: Administrador
        var authAdmin = new UsernamePasswordAuthenticationToken(
                "admin@vipcenterfit.pe", "pwd", List.of(new SimpleGrantedAuthority("reports.read"))
        );
        controller.getMetrics(authAdmin, null, null);
        Mockito.verify(service).getMetrics(true, scope);
    }
}
