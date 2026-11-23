package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.service.impl.MembershipServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MembershipPaymentLinkServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MembershipServiceImpl memberships = mock(MembershipServiceImpl.class);
    private final CulqiClient culqi = mock(CulqiClient.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MembershipPaymentLinkService service = new MembershipPaymentLinkService(
            jdbc, memberships, culqi, mapper, Clock.systemUTC(), transactions);

    @Test void unconfiguredProviderNeverCreatesAPendingLink() {
        assertThrows(IllegalStateException.class, () -> service.create(null, "staff@gymvip.pe"));
        verifyNoInteractions(jdbc, memberships);
    }

    @Test void untrustedWebhookIdDoesNotQueryProviderOrWriteMembership() {
        assertThrows(IllegalArgumentException.class, () -> service.processEvent("../../forged"));
        verifyNoInteractions(culqi, jdbc, memberships, transactions);
    }

    @Test void verifiedFailedPaymentEventDoesNotActivateMembership() throws Exception {
        String id = "evt_test_12345678";
        when(culqi.event(id)).thenReturn(mapper.readTree("{\"id\":\"" + id + "\",\"type\":\"charge.creation.failed\"}"));
        service.processEvent(id);
        verifyNoInteractions(jdbc, memberships, transactions);
    }

    @Test void productionCulqiKeyRequiresExplicitEnablement() {
        org.junit.jupiter.api.Assertions.assertFalse(new HttpCulqiClient(RestClient.builder(), "sk_live_example", false).configured());
        org.junit.jupiter.api.Assertions.assertTrue(new HttpCulqiClient(RestClient.builder(), "sk_test_example", false).configured());
        org.junit.jupiter.api.Assertions.assertTrue(new HttpCulqiClient(RestClient.builder(), "sk_live_example", true).configured());
    }
}
