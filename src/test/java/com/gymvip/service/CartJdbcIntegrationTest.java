package com.gymvip.service;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.order.SupplementOrderDtos.*;
import com.gymvip.exception.InsufficientSupplementStockException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class CartJdbcIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberWorkspaceService workspace;
    private static final Instant FIXED=Instant.parse("2026-11-25T18:30:00Z");
    private SupplementOrderService orders;
    private String email;
    private UUID product;
    @BeforeEach void prepare() {
        orders=new SupplementOrderService(jdbc,workspace,Clock.fixed(FIXED,ZoneOffset.UTC));
        email="cart-jdbc-"+UUID.randomUUID()+"@example.test";
        workspace.register(new MemberWorkspaceService.Registration(new RegisterClientRequest("Carrito","Prueba",
            LocalDate.of(1990,1,1),"PE","DNI",String.valueOf(10000000+new java.security.SecureRandom().nextInt(89999999)),null,email),"QA JDBC",true),"admin@vipcenterfit.pe");
        product=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO supplements(id,name,brand,category,presentation,net_content,description,price_pen,availability,published,stock,created_at_utc,updated_at_utc)
            VALUES (?,?,'QA','OTROS','Barra','60 g','Prueba JDBC',14.00,'DISPONIBLE',true,5,?,?)
            """,product,"Producto "+product,LocalDateTime.ofInstant(FIXED,ZoneOffset.UTC),LocalDateTime.ofInstant(FIXED,ZoneOffset.UTC));
    }
    @Test void newMemberCanCreateReuseUpdateAndClearCartWithRealPostgresUtcTimestamps() {
        var first=orders.getCart(email);
        assertTrue(first.items().isEmpty());assertEquals(first.cartId(),orders.getCart(email).cartId());
        assertEquals(LocalDateTime.ofInstant(FIXED,ZoneOffset.UTC),jdbc.queryForObject("SELECT created_at_utc FROM member_carts WHERE id=?",LocalDateTime.class,first.cartId()));
        var cart=orders.updateCartItem(email,new UpdateCartItemRequest(product,2));
        assertEquals(2,cart.totalQuantity());assertEquals(0,cart.totalPricePen().compareTo(new java.math.BigDecimal("28.00")));
        assertEquals(LocalDateTime.ofInstant(FIXED,ZoneOffset.UTC),jdbc.queryForObject("SELECT updated_at_utc FROM member_cart_items WHERE cart_id=?",LocalDateTime.class,first.cartId()));
        orders.clearCart(email);assertTrue(orders.getCart(email).items().isEmpty());
    }
    @Test void checkoutAndExpirationQueriesKeepThirtyMinuteReservationWithoutTimezoneShift() {
        orders.updateCartItem(email,new UpdateCartItemRequest(product,1));
        var order=orders.checkout(email,new CheckoutRequest("TRANSFERENCIA_MANUAL",null,"qa-"+UUID.randomUUID()));
        assertEquals(LocalDateTime.ofInstant(FIXED.plusSeconds(1800),ZoneOffset.UTC),
            jdbc.queryForObject("SELECT reservation_expires_at_utc FROM supplement_orders WHERE id=?",LocalDateTime.class,order.id()));
        assertEquals(FIXED.plusSeconds(1800),Instant.parse(order.reservationExpiresAtUtc()));
        assertEquals(0,orders.releaseExpiredReservations());
    }
    @Test void soldOutAndHiddenProductsCannotBeAddedButCanAlwaysBeRemoved() {
        orders.updateCartItem(email,new UpdateCartItemRequest(product,1));
        jdbc.update("UPDATE supplements SET availability='AGOTADO' WHERE id=?",product);
        assertThrows(com.gymvip.exception.ConflictException.class,()->orders.updateCartItem(email,new UpdateCartItemRequest(product,2)));
        assertTrue(orders.updateCartItem(email,new UpdateCartItemRequest(product,0)).items().isEmpty());
        jdbc.update("UPDATE supplements SET availability='DISPONIBLE',published=false WHERE id=?",product);
        assertThrows(com.gymvip.exception.ConflictException.class,()->orders.updateCartItem(email,new UpdateCartItemRequest(product,1)));
    }
    @Test void publicationDoesNotCreateStockAndCheckoutStillChecksExistingUnits() {
        orders.updateCartItem(email, new UpdateCartItemRequest(product, 1));
        jdbc.update("UPDATE supplements SET stock=0 WHERE id=?", product);
        assertEquals(0, orders.getCart(email).items().getFirst().availableStock());
        assertThrows(InsufficientSupplementStockException.class,
                () -> orders.updateCartItem(email, new UpdateCartItemRequest(product, 2)));
        var error = assertThrows(InsufficientSupplementStockException.class,
                () -> orders.checkout(email, new CheckoutRequest("TRANSFERENCIA_MANUAL", null, "qa-" + UUID.randomUUID())));
        assertEquals(new InsufficientSupplementStockException.Stock(product, 0, 1), error.getStock());
        assertEquals(0, jdbc.queryForObject("SELECT stock FROM supplements WHERE id=?", Integer.class, product));
    }
}
