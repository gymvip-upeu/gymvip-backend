package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.service.SupplementOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SupplementPaymentLinkServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SupplementOrderService orders = mock(SupplementOrderService.class);
    private final CulqiClient culqi = mock(CulqiClient.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SupplementPaymentLinkService service = new SupplementPaymentLinkService(
            jdbc, orders, culqi, mapper, Clock.systemUTC(), transactions);

    @Test void unconfiguredProviderCannotReserveStockOrCreateLink() {
        assertThrows(IllegalStateException.class,
                () -> service.checkoutDigital("member@example.test", "", "checkout-123"));
        verifyNoInteractions(jdbc, orders, transactions);
    }

    @Test void malformedWebhookNeverContactsProviderOrModifiesOrder() {
        assertThrows(IllegalArgumentException.class, () -> service.processEvent("../forged"));
        verifyNoInteractions(culqi, jdbc, orders, transactions);
    }

    @Test void providerConfirmedFailedEventNeverConfirmsOrder() throws Exception {
        String eventId = "evt_test_12345678";
        when(culqi.event(eventId)).thenReturn(mapper.readTree("{\"id\":\"" + eventId + "\",\"type\":\"charge.creation.failed\"}"));
        service.processEvent(eventId);
        verifyNoInteractions(jdbc, orders, transactions);
    }
}
