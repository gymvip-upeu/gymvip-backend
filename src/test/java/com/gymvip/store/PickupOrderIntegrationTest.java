package com.gymvip.store;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.service.MemberWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class PickupOrderIntegrationTest {
    @Autowired PickupOrderService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberWorkspaceService members;
    @MockitoBean MercadoPagoSandbox gateway;

    @Test
    void verifiesPriceOwnershipIdempotencyAndPaymentAmount() {
        UUID client = member();
        UUID stranger = member();
        UUID product = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        BigDecimal price = new BigDecimal("150.00");
        jdbc.update("""
                INSERT INTO supplements (id, name, brand, category, presentation, net_content,
                    description, price_pen, availability, created_at_utc, updated_at_utc)
                VALUES (?, ?, 'Marca', 'PROTEINA', 'Pote', '900 g',
                    'Producto de prueba', ?, 'DISPONIBLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, product, "Producto " + product, price);
        when(gateway.enabled()).thenReturn(true);
        var remote = new AtomicReference<MercadoPagoSandbox.ProviderOrder>();
        when(gateway.create(any(), anyString(), eq(price), eq(1))).thenAnswer(call -> {
            var response = new MercadoPagoSandbox.ProviderOrder("ORDTST12345", "created", "PEN",
                    "created", call.getArgument(0).toString(), price, BigDecimal.ZERO,
                    "https://www.mercadopago.com.pe/checkout/v1/redirect?order_id=ORDTST12345");
            remote.set(response);
            return response;
        });

        assertThrows(ConflictException.class,
                () -> service.create(client, request, product, 1, new BigDecimal("140.00")));
        var order = service.create(client, request, product, 1, price);
        assertEquals(order, service.create(client, request, product, 1, price));
        verify(gateway, times(1)).create(any(), anyString(), eq(price), eq(1));
        assertTrue(service.list(stranger).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.refresh(stranger, order.id()));
        assertEquals("PENDIENTE", order.status());

        var previous = remote.get();
        when(gateway.get(previous.id(), order.id(), price))
                .thenReturn(new MercadoPagoSandbox.ProviderOrder(previous.id(), "processed", "PEN",
                        "accredited", order.id().toString(), price, new BigDecimal("100.00"), null))
                .thenReturn(new MercadoPagoSandbox.ProviderOrder(previous.id(), "processed", "PEN",
                        "accredited", order.id().toString(), price, price, null));
        assertThrows(ResponseStatusException.class, () -> service.refresh(client, order.id()));
        assertEquals("PENDIENTE", service.list(client).getFirst().status());
        var paid = service.refresh(client, order.id());
        assertEquals("PAGADO_PRUEBA", paid.status());
        assertNull(paid.checkoutUrl());
    }

    private UUID member() {
        var registration = new RegisterClientRequest("Socio", UUID.randomUUID().toString().substring(0, 8),
                LocalDate.of(1990, 1, 1), "PE", "DNI",
                String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999)),
                "999888777", null);
        return members.register(new MemberWorkspaceService.Registration(registration, "", false),
                "admin@vipcenterfit.pe").client().id();
    }
}
