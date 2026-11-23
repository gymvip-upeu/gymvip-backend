package com.gymvip.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.service.SupplementOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test void expiredReadReturnsPaidWhenWebhookWonTheRace() {
        UUID linkId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var pending = new SupplementPaymentLinkService.LinkStatus(linkId, orderId,
                new BigDecimal("50.00"), "PENDING", "https://link.culqi.com/example", Instant.EPOCH);
        var paid = new SupplementPaymentLinkService.LinkStatus(linkId, orderId,
                new BigDecimal("50.00"), "PAID", "https://link.culqi.com/example", Instant.EPOCH);
        when(jdbc.query(contains("FROM supplement_payment_links WHERE id=?"), any(RowMapper.class), eq(linkId)))
                .thenReturn(List.of(pending), List.of(paid));
        when(jdbc.update(contains("status='EXPIRED'"), eq(linkId))).thenReturn(0);

        assertEquals("PAID", service.status(linkId, "member@example.test").status());
        verify(orders).getOrderForClient("member@example.test", orderId);
    }
}
