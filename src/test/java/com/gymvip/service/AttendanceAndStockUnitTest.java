package com.gymvip.service;

import com.gymvip.dto.attendance.AttendanceDtos.*;
import com.gymvip.dto.guardian.ClientGuardianDtos.*;
import com.gymvip.dto.response.CoverageResponse;
import com.gymvip.dto.supplement.SupplementStockDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AttendanceAndStockUnitTest {

    @Mock private JdbcClient jdbc;
    @Mock private CoverageService coverage;
    @Mock private MemberWorkspaceService workspace;
    @Mock private AuditService audit;

    private Clock clock;
    private AttendanceTerminalService terminalService;
    private AttendanceProcessingService processingService;
    private SupplementStockService stockService;
    private ClientGuardianService guardianService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneId.of("America/Lima"));
        terminalService = new AttendanceTerminalService(jdbc, clock);
        processingService = new AttendanceProcessingService(jdbc, coverage, workspace, audit, clock);
        stockService = new SupplementStockService(jdbc, audit, clock);
        guardianService = new ClientGuardianService(jdbc, audit, clock);
    }

    @Test
    @DisplayName("MemberCheckInResponse: valida estados canónicos de negocio")
    void testMemberCheckInResponseFactoryMethods() {
        LocalDate date = LocalDate.of(2026, 11, 18);
        Instant now = Instant.parse("2026-11-18T10:00:00Z");

        var rec = MemberCheckInResponse.recorded(date, now, "María Rodríguez", "MAR-WYTQ3V");
        assertEquals("ATTENDANCE_RECORDED", rec.status());
        assertTrue(rec.recorded());
        assertEquals(date, rec.attendanceDate());

        var already = MemberCheckInResponse.alreadyRegistered(date, now, "María Rodríguez", "MAR-WYTQ3V");
        assertEquals("ALREADY_REGISTERED", already.status());
        assertFalse(already.recorded());

        var rej = MemberCheckInResponse.rejected("MEMBERSHIP_EXPIRED", "Membresía vencida", "María Rodríguez", "MAR-WYTQ3V");
        assertEquals("MEMBERSHIP_EXPIRED", rej.status());
        assertFalse(rej.recorded());
        assertNull(rej.attendanceDate());
    }

    @Test
    @DisplayName("StockMovement: valida que la salida que supera el stock disponible lance ConflictException")
    void testStockMovementRejectsNegativeStock() {
        UUID supplementId = UUID.randomUUID();
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec mappedQuerySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("id"), eq(supplementId))).thenReturn(spec);
        when(spec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(mappedQuerySpec);
        // Stock actual: 5 unidades
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(new int[]{5, 2, 1}));

        StockMovementRequest request = new StockMovementRequest("SALIDA", 10, "Venta presencial");

        ConflictException ex = assertThrows(ConflictException.class, () ->
                stockService.recordMovement(supplementId, request, "admin@gymvip.local")
        );
        assertTrue(ex.getMessage().contains("Stock insuficiente"));
    }

    @Test
    @DisplayName("StockMovement: entrada de stock exitosa calcula nuevo inventario y actualiza versión")
    void testStockMovementEntranceCalculatesCorrectly() {
        UUID supplementId = UUID.randomUUID();
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec mappedQuerySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("id"), eq(supplementId))).thenReturn(spec);
        when(spec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(mappedQuerySpec);
        // Stock actual: 5 unidades
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(new int[]{5, 2, 1}));

        // Mock para UPDATE y INSERT
        when(spec.param(anyString(), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        StockMovementRequest request = new StockMovementRequest("ENTRADA", 15, "Reabastecimiento lote B-202");
        StockMovementDto result = stockService.recordMovement(supplementId, request, "admin@gymvip.local");

        assertNotNull(result);
        assertEquals(5, result.previousStock());
        assertEquals(20, result.newStock());
        assertEquals("ENTRADA", result.movementType());
        verify(audit, times(1)).recordEvent(eq("admin@gymvip.local"), eq("SUPPLEMENT_STOCK_MOVEMENT"), eq("Supplement"), eq(supplementId.toString()), anyString());
    }

    @Test
    @DisplayName("ClientGuardian: valida que el apoderado se registre con los datos correspondientes")
    void testAddGuardianSuccess() {
        UUID clientId = UUID.randomUUID();
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec mappedSpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(contains("SELECT count(*)"))).thenReturn(spec);
        when(spec.param(eq("id"), eq(clientId))).thenReturn(spec);
        when(spec.query(eq(Long.class))).thenReturn(mappedSpec);
        when(mappedSpec.single()).thenReturn(1L);

        when(jdbc.sql(contains("INSERT INTO client_guardians"))).thenReturn(spec);
        when(spec.param(anyString(), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        GuardianCreateRequest req = new GuardianCreateRequest(
                "Carlos Rodríguez Mendoza",
                "DNI",
                "09876543",
                "PADRE",
                "+51987654321",
                "carlos.rodriguez@example.com",
                true,
                "DOC-AUT-2026-001"
        );

        GuardianDto dto = guardianService.addGuardian(clientId, req, "recepcion@gymvip.local");

        assertNotNull(dto);
        assertEquals("Carlos Rodríguez Mendoza", dto.fullName());
        assertEquals("PADRE", dto.relationship());
        assertEquals("09876543", dto.documentNumber());
        verify(audit, times(1)).recordEvent(eq("recepcion@gymvip.local"), eq("ADD_CLIENT_GUARDIAN"), eq("ClientGuardian"), anyString(), anyString());
    }

    private void mockClient(UUID clientId, String name, String code) {
        JdbcClient.StatementSpec clientSpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec clientMapped = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(contains("FROM clients WHERE id = :id"))).thenReturn(clientSpec);
        when(clientSpec.param(eq("id"), eq(clientId))).thenReturn(clientSpec);
        when(clientSpec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(clientMapped);
        when(clientMapped.optional()).thenReturn(Optional.of(Map.of(
                "name", name,
                "code", code,
                "accountId", UUID.randomUUID(),
                "status", "ACTIVO"
        )));
    }

    private void mockChallenge(String token, Optional<Map<String, Object>> challengeResult) {
        JdbcClient.StatementSpec challengeSpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec challengeMapped = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(contains("FROM attendance_terminal_challenges"))).thenReturn(challengeSpec);
        when(challengeSpec.param(eq("token"), eq(token))).thenReturn(challengeSpec);
        when(challengeSpec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(challengeMapped);
        when(challengeMapped.optional()).thenReturn(challengeResult);
    }

    private void mockInsert(String tableFragment, int result) {
        JdbcClient.StatementSpec insertSpec = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(contains(tableFragment))).thenReturn(insertSpec);
        when(insertSpec.param(anyString(), any())).thenReturn(insertSpec);
        when(insertSpec.update()).thenReturn(result);
    }

    @Test
    @DisplayName("SelfCheckIn: Desafío inexistente debe ser rechazado con CHALLENGE_INVALID")
    void testSelfCheckInRejectsInvalidChallenge() {
        UUID clientId = UUID.randomUUID();
        when(workspace.ownClient("socio@gymvip.local")).thenReturn(clientId);

        mockClient(clientId, "María Rodríguez", "MAR-WYTQ3V");
        mockChallenge("gymvip:challenge:v1:fake-token", Optional.empty());
        mockInsert("attendance_rejection_logs", 1);

        MemberCheckInResponse response = processingService.processMemberSelfCheckIn(
                "socio@gymvip.local",
                "gymvip:challenge:v1:fake-token",
                UUID.randomUUID(),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals("CHALLENGE_INVALID", response.status());
        assertFalse(response.recorded());
        assertTrue(response.message().contains("inválido"));
    }

    @Test
    @DisplayName("SelfCheckIn: Desafío expirado debe ser rechazado con CHALLENGE_EXPIRED")
    void testSelfCheckInRejectsExpiredChallenge() {
        UUID clientId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        when(workspace.ownClient("socio@gymvip.local")).thenReturn(clientId);

        mockClient(clientId, "María Rodríguez", "MAR-WYTQ3V");
        Instant expiredInstant = clock.instant().minusSeconds(5);
        mockChallenge("gymvip:challenge:v1:expired-token", Optional.of(Map.of(
                "terminalId", terminalId,
                "terminalName", "Recepción 1",
                "terminalStatus", "ACTIVA",
                "expiresAt", expiredInstant
        )));
        mockInsert("attendance_rejection_logs", 1);

        MemberCheckInResponse response = processingService.processMemberSelfCheckIn(
                "socio@gymvip.local",
                "gymvip:challenge:v1:expired-token",
                UUID.randomUUID(),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals("CHALLENGE_EXPIRED", response.status());
        assertFalse(response.recorded());
        assertTrue(response.message().contains("expirado"));
    }

    @Test
    @DisplayName("SelfCheckIn: Membresía vencida es rechazada con MEMBERSHIP_EXPIRED")
    void testSelfCheckInRejectsExpiredMembership() {
        UUID clientId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        when(workspace.ownClient("socio@gymvip.local")).thenReturn(clientId);

        mockClient(clientId, "María Rodríguez", "MAR-WYTQ3V");
        Instant validExpiresAt = clock.instant().plusSeconds(15);
        mockChallenge("gymvip:challenge:v1:valid-token", Optional.of(Map.of(
                "terminalId", terminalId,
                "terminalName", "Recepción 1",
                "terminalStatus", "ACTIVA",
                "expiresAt", validExpiresAt
        )));

        CoverageResponse coverageMock = mock(CoverageResponse.class);
        when(coverageMock.allowed()).thenReturn(false);
        when(coverageMock.state()).thenReturn("VENCIDO");
        when(coverageMock.reason()).thenReturn("La membresía finalizó el 2026-11-10.");
        when(coverage.verify(clientId)).thenReturn(coverageMock);

        mockInsert("attendance_rejection_logs", 1);

        MemberCheckInResponse response = processingService.processMemberSelfCheckIn(
                "socio@gymvip.local",
                "gymvip:challenge:v1:valid-token",
                UUID.randomUUID(),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals("MEMBERSHIP_EXPIRED", response.status());
        assertFalse(response.recorded());
        assertTrue(response.message().contains("Acceso no autorizado"));
    }

    @Test
    @DisplayName("SelfCheckIn: Socio activo con membresía vigente registra asistencia exitosamente")
    void testSelfCheckInSuccess() {
        UUID clientId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        when(workspace.ownClient("socio@gymvip.local")).thenReturn(clientId);

        mockClient(clientId, "María Rodríguez", "MAR-WYTQ3V");
        Instant validExpiresAt = clock.instant().plusSeconds(15);
        mockChallenge("gymvip:challenge:v1:valid-token", Optional.of(Map.of(
                "terminalId", terminalId,
                "terminalName", "Recepción 1",
                "terminalStatus", "ACTIVA",
                "expiresAt", validExpiresAt
        )));

        CoverageResponse coverageMock = mock(CoverageResponse.class);
        when(coverageMock.allowed()).thenReturn(true);
        when(coverage.verify(clientId)).thenReturn(coverageMock);

        mockInsert("INSERT INTO member_attendance", 1);

        MemberCheckInResponse response = processingService.processMemberSelfCheckIn(
                "socio@gymvip.local",
                "gymvip:challenge:v1:valid-token",
                UUID.randomUUID(),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals("ATTENDANCE_RECORDED", response.status());
        assertTrue(response.recorded());
        assertEquals("María Rodríguez", response.memberName());
        assertEquals("MAR-WYTQ3V", response.memberCode());
        verify(audit, times(1)).recordEvent(eq("socio@gymvip.local"), eq("MEMBER_SELF_CHECK_IN"), eq("Client"), eq(clientId.toString()), anyString());
    }

    @Test
    @DisplayName("SelfCheckIn: Escaneo repetido en el mismo día retorna ALREADY_REGISTERED sin error")
    void testSelfCheckInDuplicateReturnsAlreadyRegistered() {
        UUID clientId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        when(workspace.ownClient("socio@gymvip.local")).thenReturn(clientId);

        mockClient(clientId, "María Rodríguez", "MAR-WYTQ3V");
        Instant validExpiresAt = clock.instant().plusSeconds(15);
        mockChallenge("gymvip:challenge:v1:valid-token", Optional.of(Map.of(
                "terminalId", terminalId,
                "terminalName", "Recepción 1",
                "terminalStatus", "ACTIVA",
                "expiresAt", validExpiresAt
        )));

        CoverageResponse coverageMock = mock(CoverageResponse.class);
        when(coverageMock.allowed()).thenReturn(true);
        when(coverage.verify(clientId)).thenReturn(coverageMock);

        mockInsert("INSERT INTO member_attendance", 0);

        MemberCheckInResponse response = processingService.processMemberSelfCheckIn(
                "socio@gymvip.local",
                "gymvip:challenge:v1:valid-token",
                UUID.randomUUID(),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals("ALREADY_REGISTERED", response.status());
        assertFalse(response.recorded());
        assertTrue(response.message().contains("ya fue registrada"));
    }
}
