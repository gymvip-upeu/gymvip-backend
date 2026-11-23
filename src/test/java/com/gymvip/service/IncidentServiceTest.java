package com.gymvip.service;

import com.gymvip.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IncidentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final IncidentService service = new IncidentService(jdbc, audit, Clock.systemUTC());

    @Test void closedIncidentCannotBeClosedAgainOrAuditedAsSuccess() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);
        assertThrows(ConflictException.class,
                () -> service.close(UUID.randomUUID(), new IncidentService.CloseRequest("Revisado"), "auditor@gymvip.pe"));
        verifyNoInteractions(audit);
    }

    @Test void closeRequiresNonBlankReasonAndListRejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.close(UUID.randomUUID(), new IncidentService.CloseRequest("  "), "auditor@gymvip.pe"));
        assertThrows(IllegalArgumentException.class, () -> service.list("BORRADA"));
        verifyNoInteractions(jdbc, audit);
    }
}
