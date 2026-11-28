package com.gymvip.service;

import com.gymvip.exception.ConflictException;
import com.gymvip.entity.Account;
import com.gymvip.security.TenantScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final TenantScopeResolver tenants = mock(TenantScopeResolver.class);
    private final IncidentService service = new IncidentService(jdbc, audit, Clock.systemUTC(), tenants);

    @Test void closedIncidentCannotBeClosedAgainOrAuditedAsSuccess() {
        Account account = new Account();
        UUID organization = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(tenants.resolveActorByEmail("auditor@gymvip.pe")).thenReturn(account);
        when(tenants.effectiveOrganizationId(account, null)).thenReturn(organization);
        when(tenants.effectiveBranchId(account, null)).thenReturn(branch);
        assertThrows(ConflictException.class,
                () -> service.close(id, new IncidentService.CloseRequest("Revisado"), "auditor@gymvip.pe"));
        verify(jdbc).update(contains("organization_id=? AND (CAST(? AS uuid) IS NULL OR branch_id=?)"),
                eq("auditor@gymvip.pe"), any(), eq("Revisado"), eq(id), eq(organization), eq(branch), eq(branch));
        verifyNoInteractions(audit);
    }

    @Test void closeRequiresNonBlankReasonAndListRejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.close(UUID.randomUUID(), new IncidentService.CloseRequest("  "), "auditor@gymvip.pe"));
        assertThrows(IllegalArgumentException.class, () -> service.list("BORRADA", "auditor@gymvip.pe"));
        verifyNoInteractions(jdbc, audit);
    }
}
