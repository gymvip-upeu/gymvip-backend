package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.MembershipQuoteRequest;
import com.gymvip.dto.request.MembershipSaleRequest;
import com.gymvip.repository.MembershipRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.impl.MembershipServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MembershipServiceOnlineBoundaryTest {
    @Test void publicSaleCannotClaimAnUnverifiedCulqiPayment() {
        var repository = mock(MembershipRepository.class);
        var actor = UUID.randomUUID();
        when(repository.actor("staff@gymvip.pe")).thenReturn(actor);
        when(repository.operation(actor, "manual123")).thenReturn(Optional.empty());
        var service = new MembershipServiceImpl(repository, mock(AuditService.class),
                new ObjectMapper(), Clock.systemUTC(), "EFECTIVO,YAPE");
        var request = new MembershipSaleRequest(UUID.randomUUID(), UUID.randomUUID(),
                MembershipQuoteRequest.Operation.ENROLL, "a".repeat(64), new BigDecimal("60.00"),
                "CULQI", "chr_test_unverified");

        assertThrows(IllegalArgumentException.class,
                () -> service.sell(request, "manual123", "staff@gymvip.pe"));
        verify(repository, never()).createContract(any(), any(), any(), any(), any());
        verify(repository, never()).addPayment(any(), any());
    }
}
