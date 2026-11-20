package com.gymvip.service;

import com.gymvip.dto.order.SupplementOrderDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class SupplementOrderService {

    private final JdbcTemplate jdbc;
    private final MemberWorkspaceService workspaceService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SupplementOrderService(JdbcTemplate jdbc, MemberWorkspaceService workspaceService, Clock clock) {
        this.jdbc = jdbc;
        this.workspaceService = workspaceService;
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant();
    }

    private String generateSecureToken(String prefix, int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private UUID getAccountIdByEmail(String email) {
        var list = jdbc.query("SELECT id FROM accounts WHERE email = ?", (rs, n) -> rs.getObject("id", UUID.class), email);
        if (list.isEmpty()) throw new AccessDeniedException("Cuenta no encontrada para " + email);
        return list.get(0);
    }

    // =========================================================================
    // 1. GESTIÓN DEL CARRITO PERSISTIDO POR USUARIO
    // =========================================================================

    private UUID getOrCreateCartId(UUID accountId) {
        var existing = jdbc.query("SELECT id FROM member_carts WHERE account_id = ?", (rs, n) -> rs.getObject("id", UUID.class), accountId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID cartId = UUID.randomUUID();
        jdbc.update("INSERT INTO member_carts (id, account_id, created_at_utc, updated_at_utc) VALUES (?, ?, ?, ?)",
                cartId, accountId, now(), now());
        return cartId;
    }

    @Transactional(readOnly = true)
    public CartSummaryDto getCart(String userEmail) {
        UUID accountId = getAccountIdByEmail(userEmail);
        UUID cartId = getOrCreateCartId(accountId);

        List<CartItemDto> items = jdbc.query("""
            SELECT i.id, i.supplement_id, s.name, s.brand, s.presentation, s.flavor,
                   s.price_pen, s.stock, i.quantity
            FROM member_cart_items i
            JOIN supplements s ON s.id = i.supplement_id
            WHERE i.cart_id = ?
            ORDER BY i.created_at_utc ASC
        """, (rs, n) -> mapCartItem(rs), cartId);

        BigDecimal total = items.stream()
                .map(CartItemDto::subtotalPen)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int totalQty = items.stream().mapToInt(CartItemDto::quantity).sum();

        return new CartSummaryDto(cartId, items, total, totalQty);
    }

    private CartItemDto mapCartItem(ResultSet rs) throws SQLException {
        UUID itemId = rs.getObject("id", UUID.class);
        UUID supId = rs.getObject("supplement_id", UUID.class);
        String name = rs.getString("name");
        String brand = rs.getString("brand");
        String pres = rs.getString("presentation");
        String flavor = rs.getString("flavor");
        BigDecimal price = rs.getBigDecimal("price_pen").setScale(2, RoundingMode.HALF_UP);
        int stock = rs.getInt("stock");
        int qty = rs.getInt("quantity");
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

        return new CartItemDto(itemId, supId, name, brand, pres, flavor, price, qty, subtotal, stock, null);
    }

    public CartSummaryDto updateCartItem(String userEmail, UpdateCartItemRequest req) {
        UUID accountId = getAccountIdByEmail(userEmail);
        UUID cartId = getOrCreateCartId(accountId);

        if (req.quantity() <= 0) {
            jdbc.update("DELETE FROM member_cart_items WHERE cart_id = ? AND supplement_id = ?", cartId, req.supplementId());
        } else {
            int cappedQty = Math.min(req.quantity(), 20);
            jdbc.update("""
                INSERT INTO member_cart_items (id, cart_id, supplement_id, quantity, created_at_utc, updated_at_utc)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (cart_id, supplement_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at_utc = EXCLUDED.updated_at_utc
            """, UUID.randomUUID(), cartId, req.supplementId(), cappedQty, now(), now());
        }
        jdbc.update("UPDATE member_carts SET updated_at_utc = ? WHERE id = ?", now(), cartId);
        return getCart(userEmail);
    }

    public void clearCart(String userEmail) {
        UUID accountId = getAccountIdByEmail(userEmail);
        UUID cartId = getOrCreateCartId(accountId);
        jdbc.update("DELETE FROM member_cart_items WHERE cart_id = ?", cartId);
        jdbc.update("UPDATE member_carts SET updated_at_utc = ? WHERE id = ?", now(), cartId);
    }

    // =========================================================================
    // 2. CHECKOUT Y CREACIÓN DE PEDIDO CON RESERVA DE STOCK
    // =========================================================================

    public OrderDetailDto checkout(String userEmail, CheckoutRequest req) {
        UUID accountId = getAccountIdByEmail(userEmail);
        UUID clientId = workspaceService.findOwnClient(userEmail)
                .orElseThrow(() -> new AccessDeniedException("Solo los socios activos pueden realizar pedidos de suplementos."));

        // Manejo de idempotencia
        if (req != null && req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            var existing = jdbc.query("SELECT id FROM supplement_orders WHERE idempotency_key = ?",
                    (rs, n) -> rs.getObject("id", UUID.class), req.idempotencyKey().trim());
            if (!existing.isEmpty()) {
                return getOrderByIdInternal(existing.get(0));
            }
        }

        UUID cartId = getOrCreateCartId(accountId);
        var cartRows = jdbc.query("""
            SELECT i.supplement_id, i.quantity, s.name, s.brand, s.presentation, s.flavor,
                   s.price_pen, s.stock, s.availability, s.published
            FROM member_cart_items i
            JOIN supplements s ON s.id = i.supplement_id
            WHERE i.cart_id = ?
        """, (rs, n) -> rs, cartId);

        if (cartRows.isEmpty()) {
            throw new ConflictException("El carrito de compras está vacío. Agregue productos antes de confirmar el pedido.");
        }

        // Generar identificador legible de pedido (ORD-AAAAMMDD-XXXX)
        String datePrefix = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderNumber = "ORD-" + datePrefix + "-" + (1000 + secureRandom.nextInt(9000));
        UUID orderId = UUID.randomUUID();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        Instant reservationExpiry = now().plus(30, ChronoUnit.MINUTES);

        // Bloqueo pesimista y reserva atómica de stock
        for (var row : cartRows) {
            UUID supId;
            int reqQty;
            try {
                supId = row.getObject("supplement_id", UUID.class);
                reqQty = row.getInt("quantity");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            var locked = jdbc.query("""
                SELECT id, name, brand, presentation, flavor, price_pen, stock, availability, published, version
                FROM supplements
                WHERE id = ?
                FOR UPDATE
            """, (rs, n) -> Map.of(
                    "name", rs.getString("name"),
                    "brand", rs.getString("brand"),
                    "presentation", rs.getString("presentation"),
                    "flavor", rs.getString("flavor") != null ? rs.getString("flavor") : "",
                    "price_pen", rs.getBigDecimal("price_pen"),
                    "stock", rs.getInt("stock"),
                    "availability", rs.getString("availability"),
                    "published", rs.getBoolean("published")
            ), supId);

            if (locked.isEmpty()) {
                throw new ResourceNotFoundException("Producto no encontrado durante el checkout.");
            }

            var product = locked.get(0);
            int currentStock = (int) product.get("stock");
            boolean published = (boolean) product.get("published");
            String availability = (String) product.get("availability");

            if (!published || !"DISPONIBLE".equalsIgnoreCase(availability) || currentStock < reqQty) {
                throw new ConflictException("Stock insuficiente para '" + product.get("name") + "'. Disponible: " + currentStock + ", solicitado: " + reqQty);
            }

            // Consultar si hay promoción vigente
            BigDecimal regularPrice = ((BigDecimal) product.get("price_pen")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal finalPrice = regularPrice;
            UUID promoId = null;
            String promoName = null;

            var promoRows = jdbc.query("""
                SELECT id, name, offer_price_pen
                FROM supplement_promotions
                WHERE supplement_id = ? AND enabled = true
                  AND CURRENT_DATE BETWEEN start_date AND end_date
                ORDER BY offer_price_pen ASC LIMIT 1
            """, (rs, n) -> Map.of(
                    "id", rs.getObject("id", UUID.class),
                    "name", rs.getString("name"),
                    "offer_price_pen", rs.getBigDecimal("offer_price_pen")
            ), supId);

            if (!promoRows.isEmpty()) {
                promoId = (UUID) promoRows.get(0).get("id");
                promoName = (String) promoRows.get(0).get("name");
                finalPrice = ((BigDecimal) promoRows.get(0).get("offer_price_pen")).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal lineRegularTotal = regularPrice.multiply(BigDecimal.valueOf(reqQty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineFinalTotal = finalPrice.multiply(BigDecimal.valueOf(reqQty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineDiscount = lineRegularTotal.subtract(lineFinalTotal).setScale(2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineRegularTotal);
            discountTotal = discountTotal.add(lineDiscount);

            // Descontar stock reservándolo
            int newStock = currentStock - reqQty;
            String newAvailability = newStock > 0 ? "DISPONIBLE" : "AGOTADO";
            jdbc.update("UPDATE supplements SET stock = ?, availability = ?, updated_at_utc = ? WHERE id = ?",
                    newStock, newAvailability, now(), supId);

            // Registrar movimiento de kardex
            jdbc.update("""
                INSERT INTO supplement_stock_movements (id, supplement_id, movement_type, quantity, previous_stock, new_stock, reason, actor, created_at_utc)
                VALUES (?, ?, 'SALIDA', ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), supId, reqQty, currentStock, newStock, "RESERVA_CHECKOUT_" + orderNumber, userEmail, now());

            // Guardar snapshot de ítem de pedido
            jdbc.update("""
                INSERT INTO supplement_order_items (id, order_id, supplement_id, product_name, product_brand, product_presentation, product_flavor,
                                                   unit_regular_price_pen, unit_final_price_pen, applied_promotion_id, applied_promotion_name,
                                                   quantity, line_total_pen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), orderId, supId, product.get("name"), product.get("brand"), product.get("presentation"), product.get("flavor"),
                    regularPrice, finalPrice, promoId, promoName, reqQty, lineFinalTotal);
        }

        BigDecimal total = subtotal.subtract(discountTotal).setScale(2, RoundingMode.HALF_UP);
        String paymentMethod = (req != null && req.paymentMethod() != null && !req.paymentMethod().isBlank())
                ? req.paymentMethod().trim().toUpperCase() : "TRANSFERENCIA_MANUAL";
        String customerNotes = (req != null) ? req.customerNotes() : null;
        String idempotencyKey = (req != null && req.idempotencyKey() != null && !req.idempotencyKey().isBlank())
                ? req.idempotencyKey().trim() : null;

        // Insertar pedido en estado PENDIENTE_PAGO
        jdbc.update("""
            INSERT INTO supplement_orders (id, order_number, client_id, account_id, order_status, payment_status, fulfillment_status,
                                          subtotal_pen, discount_pen, total_pen, payment_method, reservation_expires_at_utc,
                                          stock_reserved, stock_released, customer_notes, idempotency_key, created_at_utc, updated_at_utc)
            VALUES (?, ?, ?, ?, 'PENDIENTE_PAGO', 'PENDIENTE', 'PENDIENTE', ?, ?, ?, ?, ?, true, false, ?, ?, ?, ?)
        """, orderId, orderNumber, clientId, accountId, subtotal, discountTotal, total, paymentMethod,
                reservationExpiry, customerNotes, idempotencyKey, now(), now());

        // Limpiar el carrito
        jdbc.update("DELETE FROM member_cart_items WHERE cart_id = ?", cartId);

        // Auditoría
        recordOrderAudit(orderId, "ORDER_CREATED", null, "PENDIENTE_PAGO", userEmail,
                "Pedido creado con reserva de stock por 30 minutos. Total: S/ " + total);

        return getOrderByIdInternal(orderId);
    }

    // =========================================================================
    // 3. REGISTRO DE EVIDENCIA Y PAGO POR EL CLIENTE
    // =========================================================================

    public OrderDetailDto submitPaymentEvidence(String userEmail, UUID orderId, PaymentEvidenceSubmitRequest req) {
        UUID accountId = getAccountIdByEmail(userEmail);

        var order = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, total_pen, payment_reference, stock_released, reservation_expires_at_utc
            FROM supplement_orders
            WHERE id = ? AND account_id = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "order_number", rs.getString("order_number"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "total_pen", rs.getBigDecimal("total_pen"),
                "stock_released", rs.getBoolean("stock_released"),
                "reservation_expires", rs.getTimestamp("reservation_expires_at_utc") != null ? rs.getTimestamp("reservation_expires_at_utc").toInstant() : now()
        ), orderId, accountId);

        if (order.isEmpty()) {
            throw new ResourceNotFoundException("Pedido no encontrado.");
        }

        var orderData = order.get(0);
        String currentOrderStatus = (String) orderData.get("order_status");
        boolean stockReleased = (boolean) orderData.get("stock_released");
        Instant expires = (Instant) orderData.get("reservation_expires");

        if (stockReleased || now().isAfter(expires)) {
            throw new ConflictException("La reserva de stock de este pedido expiró. Por favor genere un nuevo pedido desde el catálogo.");
        }

        if (!List.of("CREADO", "PENDIENTE_PAGO", "PAGADO_POR_VERIFICAR").contains(currentOrderStatus)) {
            throw new ConflictException("El pedido no se encuentra en estado pendiente de pago (Estado actual: " + currentOrderStatus + ")");
        }

        String ref = req.paymentReference().trim();

        // Control estricto anti-reutilización de referencia de pago
        var duplicateRef = jdbc.query("""
            SELECT order_number FROM supplement_orders
            WHERE payment_reference = ? AND id <> ? AND payment_status IN ('EVIDENCIA_SUBIDA', 'VERIFICADO')
        """, (rs, n) -> rs.getString("order_number"), ref, orderId);

        if (!duplicateRef.isEmpty()) {
            throw new ConflictException("La referencia de pago '" + ref + "' ya fue registrada previamente en el pedido " + duplicateRef.get(0) + ".");
        }

        jdbc.update("""
            UPDATE supplement_orders
            SET payment_reference = ?, payment_evidence_url = ?, payment_evidence_notes = ?,
                payment_evidence_at_utc = ?, order_status = 'PAGADO_POR_VERIFICAR',
                payment_status = 'EVIDENCIA_SUBIDA', updated_at_utc = ?
            WHERE id = ?
        """, ref, req.paymentEvidenceUrl(), req.paymentNotes(), now(), now(), orderId);

        recordOrderAudit(orderId, "PAYMENT_EVIDENCE_SUBMITTED", currentOrderStatus, "PAGADO_POR_VERIFICAR", userEmail,
                "Evidencia de pago subida con referencia: " + ref);

        return getOrderByIdInternal(orderId);
    }

    // =========================================================================
    // 4. VERIFICACIÓN Y APROBACIÓN DE PAGO POR RECEPCIÓN / ADMIN
    // =========================================================================

    public OrderDetailDto approvePayment(UUID orderId, PaymentApprovalRequest req, String adminActor) {
        var order = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, total_pen, stock_released, client_id
            FROM supplement_orders
            WHERE id = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "order_number", rs.getString("order_number"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "total_pen", rs.getBigDecimal("total_pen"),
                "stock_released", rs.getBoolean("stock_released"),
                "client_id", rs.getObject("client_id", UUID.class)
        ), orderId);

        if (order.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        var data = order.get(0);
        String currentOrderStatus = (String) data.get("order_status");
        String currentPaymentStatus = (String) data.get("payment_status");
        BigDecimal expectedTotal = (BigDecimal) data.get("total_pen");
        boolean stockReleased = (boolean) data.get("stock_released");
        UUID clientId = (UUID) data.get("client_id");

        if ("VERIFICADO".equalsIgnoreCase(currentPaymentStatus)) {
            // Idempotente si ya fue aprobado
            return getOrderByIdInternal(orderId);
        }

        if (stockReleased) {
            throw new ConflictException("No se puede aprobar un pedido cuyo stock ya fue liberado por cancelación o expiración.");
        }

        BigDecimal verifiedAmount = req.verifiedAmountPen().setScale(2, RoundingMode.HALF_UP);
        if (verifiedAmount.compareTo(expectedTotal) < 0) {
            throw new ConflictException("El monto verificado (S/ " + verifiedAmount + ") es menor al total requerido (S/ " + expectedTotal + ").");
        }

        jdbc.update("""
            UPDATE supplement_orders
            SET order_status = 'CONFIRMADO_PREPARACION', payment_status = 'VERIFICADO',
                fulfillment_status = 'EN_PREPARACION', verified_by = ?, verified_at_utc = ?,
                verified_amount_pen = ?, updated_at_utc = ?
            WHERE id = ?
        """, adminActor, now(), verifiedAmount, now(), orderId);

        recordOrderAudit(orderId, "PAYMENT_APPROVED", currentOrderStatus, "CONFIRMADO_PREPARACION", adminActor,
                "Pago verificado por S/ " + verifiedAmount + ". Notas: " + (req.notes() != null ? req.notes() : ""));

        // Notificación Outbox al socio
        queueOutboxPush(clientId, "Pago Aprobado: Pedido " + data.get("order_number"),
                "Hemos verificado tu pago de S/ " + verifiedAmount + ". Tu pedido está en preparación.");

        return getOrderByIdInternal(orderId);
    }

    public OrderDetailDto rejectPayment(UUID orderId, PaymentRejectionRequest req, String adminActor) {
        var order = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, stock_reserved, stock_released, client_id
            FROM supplement_orders
            WHERE id = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "order_number", rs.getString("order_number"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "stock_reserved", rs.getBoolean("stock_reserved"),
                "stock_released", rs.getBoolean("stock_released"),
                "client_id", rs.getObject("client_id", UUID.class)
        ), orderId);

        if (order.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        var data = order.get(0);
        String currentOrderStatus = (String) data.get("order_status");
        boolean stockReserved = (boolean) data.get("stock_reserved");
        boolean stockReleased = (boolean) data.get("stock_released");
        UUID clientId = (UUID) data.get("client_id");

        // Liberar stock si estaba reservado y no liberado
        if (stockReserved && !stockReleased) {
            releaseStockForOrder(orderId, (String) data.get("order_number"), adminActor);
        }

        jdbc.update("""
            UPDATE supplement_orders
            SET order_status = 'RECHAZADO', payment_status = 'RECHAZADO',
                fulfillment_status = 'ANULADO', rejection_reason = ?, stock_released = true, updated_at_utc = ?
            WHERE id = ?
        """, req.reason().trim(), now(), orderId);

        recordOrderAudit(orderId, "PAYMENT_REJECTED", currentOrderStatus, "RECHAZADO", adminActor,
                "Pago rechazado: " + req.reason().trim());

        queueOutboxPush(clientId, "Aviso de Pago: Pedido " + data.get("order_number"),
                "Tu comprobante de pago fue observado: " + req.reason().trim());

        return getOrderByIdInternal(orderId);
    }

    // =========================================================================
    // 5. PREPARACIÓN Y EMISIÓN DE TICKET DE RETIRO
    // =========================================================================

    public OrderDetailDto markReadyForPickup(UUID orderId, String adminActor) {
        var order = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, fulfillment_status, pickup_token, client_id
            FROM supplement_orders
            WHERE id = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "order_number", rs.getString("order_number"),
                "payment_status", rs.getString("payment_status"),
                "fulfillment_status", rs.getString("fulfillment_status"),
                "pickup_token", rs.getString("pickup_token") != null ? rs.getString("pickup_token") : "",
                "client_id", rs.getObject("client_id", UUID.class)
        ), orderId);

        if (order.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        var data = order.get(0);
        String paymentStatus = (String) data.get("payment_status");
        if (!"VERIFICADO".equalsIgnoreCase(paymentStatus)) {
            throw new ConflictException("No se puede marcar listo para retiro un pedido cuyo pago no ha sido verificado.");
        }

        String existingToken = (String) data.get("pickup_token");
        String pickupToken = existingToken.isBlank() ? generateSecureToken("rtk_", 18) : existingToken;
        UUID clientId = (UUID) data.get("client_id");

        jdbc.update("""
            UPDATE supplement_orders
            SET order_status = 'LISTO_PARA_RETIRO', fulfillment_status = 'LISTO',
                pickup_token = ?, pickup_token_issued_at_utc = COALESCE(pickup_token_issued_at_utc, ?),
                updated_at_utc = ?
            WHERE id = ?
        """, pickupToken, now(), now(), orderId);

        recordOrderAudit(orderId, "ORDER_READY_FOR_PICKUP", "CONFIRMADO_PREPARACION", "LISTO_PARA_RETIRO", adminActor,
                "Pedido empaquetado y listo para retiro en mostrador.");

        queueOutboxPush(clientId, "¡Pedido Listo para Retiro!",
                "Tu pedido " + data.get("order_number") + " está listo. Acércate a recepción y presenta tu código QR de retiro.");

        return getOrderByIdInternal(orderId);
    }

    @Transactional(readOnly = true)
    public PickupTicketDto getPickupTicket(String userEmail, UUID orderId) {
        UUID accountId = getAccountIdByEmail(userEmail);

        var order = jdbc.query("""
            SELECT o.id, o.order_number, o.order_status, o.payment_status, o.fulfillment_status,
                   o.total_pen, o.pickup_token, o.pickup_token_issued_at_utc, o.pickup_token_consumed_at_utc,
                   c.first_name, c.last_name
            FROM supplement_orders o
            JOIN clients c ON c.id = o.client_id
            WHERE o.id = ? AND o.account_id = ?
        """, (rs, n) -> rs, orderId, accountId);

        if (order.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        try {
            var row = order.get(0);
            String payStatus = row.getString("payment_status");
            String fulfillStatus = row.getString("fulfillment_status");
            String pickupToken = row.getString("pickup_token");

            if (!"VERIFICADO".equalsIgnoreCase(payStatus) || !"LISTO".equalsIgnoreCase(fulfillStatus) || pickupToken == null || pickupToken.isBlank()) {
                throw new ConflictException("El ticket de retiro solo está disponible cuando el pago está verificado y el pedido está listo para entrega.");
            }

            String orderNumber = row.getString("order_number");
            String clientName = row.getString("first_name") + " " + row.getString("last_name");
            BigDecimal total = row.getBigDecimal("total_pen");
            String orderStatus = row.getString("order_status");
            String issuedAt = row.getTimestamp("pickup_token_issued_at_utc") != null
                    ? row.getTimestamp("pickup_token_issued_at_utc").toInstant().toString() : now().toString();
            var consumedTs = row.getTimestamp("pickup_token_consumed_at_utc");
            boolean isConsumed = consumedTs != null;
            String consumedAt = isConsumed ? consumedTs.toInstant().toString() : null;

            List<OrderItemDto> items = getOrderItems(orderId);

            return new PickupTicketDto(
                    orderNumber,
                    pickupToken,
                    clientName,
                    "SOCIO ACREDITADO",
                    items,
                    total,
                    orderStatus,
                    payStatus,
                    fulfillStatus,
                    issuedAt,
                    isConsumed,
                    consumedAt,
                    "Presente este código QR en el mostrador de recepción del gimnasio para reclamar sus suplementos.",
                    "Este ticket es una credencial de entrega física y no constituye comprobante de pago electrónico SUNAT."
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 6. INSPECCIÓN Y ENTREGA ATÓMICA DE RETIRO
    // =========================================================================

    @Transactional(readOnly = true)
    public PickupTicketDto inspectPickupToken(String token) {
        if (token == null || token.isBlank()) throw new ConflictException("Token de retiro no provisto.");

        var rows = jdbc.query("""
            SELECT o.id, o.order_number, o.order_status, o.payment_status, o.fulfillment_status,
                   o.total_pen, o.pickup_token, o.pickup_token_issued_at_utc, o.pickup_token_consumed_at_utc,
                   c.first_name, c.last_name, c.member_code
            FROM supplement_orders o
            JOIN clients c ON c.id = o.client_id
            WHERE o.pickup_token = ?
        """, (rs, n) -> rs, token.trim());

        if (rows.isEmpty()) throw new ResourceNotFoundException("Ticket de retiro no válido o no encontrado en el servidor.");

        try {
            var row = rows.get(0);
            UUID orderId = row.getObject("id", UUID.class);
            String orderNumber = row.getString("order_number");
            String clientName = row.getString("first_name") + " " + row.getString("last_name");
            String memberCode = row.getString("member_code");
            BigDecimal total = row.getBigDecimal("total_pen");
            String orderStatus = row.getString("order_status");
            String payStatus = row.getString("payment_status");
            String fulfillStatus = row.getString("fulfillment_status");
            String issuedAt = row.getTimestamp("pickup_token_issued_at_utc") != null
                    ? row.getTimestamp("pickup_token_issued_at_utc").toInstant().toString() : "";
            var consumedTs = row.getTimestamp("pickup_token_consumed_at_utc");
            boolean isConsumed = consumedTs != null;
            String consumedAt = isConsumed ? consumedTs.toInstant().toString() : null;

            List<OrderItemDto> items = getOrderItems(orderId);

            return new PickupTicketDto(
                    orderNumber,
                    token.trim(),
                    clientName,
                    "CÓDIGO: " + memberCode,
                    items,
                    total,
                    orderStatus,
                    payStatus,
                    fulfillStatus,
                    issuedAt,
                    isConsumed,
                    consumedAt,
                    "Verifique la identidad del socio y los suplementos listados antes de pulsar Confirmar Entrega.",
                    "Este ticket es de control interno y no reemplaza comprobantes tributarios."
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public OrderDetailDto fulfillOrderDelivery(String token, String adminActor, String notes) {
        if (token == null || token.isBlank()) throw new ConflictException("Token de retiro no provisto.");

        var rows = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, fulfillment_status,
                   pickup_token_consumed_at_utc, client_id
            FROM supplement_orders
            WHERE pickup_token = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "id", rs.getObject("id", UUID.class),
                "order_number", rs.getString("order_number"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "fulfillment_status", rs.getString("fulfillment_status"),
                "consumed_at", rs.getTimestamp("pickup_token_consumed_at_utc") != null,
                "client_id", rs.getObject("client_id", UUID.class)
        ), token.trim());

        if (rows.isEmpty()) throw new ResourceNotFoundException("Ticket de retiro no encontrado.");

        var data = rows.get(0);
        UUID orderId = (UUID) data.get("id");
        String orderNumber = (String) data.get("order_number");
        String orderStatus = (String) data.get("order_status");
        String fulfillStatus = (String) data.get("fulfillment_status");
        boolean isConsumed = (boolean) data.get("consumed_at");
        UUID clientId = (UUID) data.get("client_id");

        if (isConsumed || "ENTREGADO".equalsIgnoreCase(fulfillStatus)) {
            throw new ConflictException("Este ticket de retiro ya fue consumido anteriormente. El pedido ya fue entregado.");
        }

        if (!"LISTO".equalsIgnoreCase(fulfillStatus) && !"LISTO_PARA_RETIRO".equalsIgnoreCase(orderStatus)) {
            throw new ConflictException("El pedido no se encuentra en estado LISTO_PARA_RETIRO (Estado: " + orderStatus + ")");
        }

        jdbc.update("""
            UPDATE supplement_orders
            SET order_status = 'ENTREGADO', fulfillment_status = 'ENTREGADO',
                pickup_token_consumed_at_utc = ?, fulfilled_by = ?, fulfilled_at_utc = ?,
                updated_at_utc = ?
            WHERE id = ?
        """, now(), adminActor, now(), now(), orderId);

        recordOrderAudit(orderId, "ORDER_FULFILLED_DELIVERED", orderStatus, "ENTREGADO", adminActor,
                "Entrega física confirmada en mostrador por " + adminActor + ". " + (notes != null ? notes : ""));

        queueOutboxPush(clientId, "Entrega Exitosa: Pedido " + orderNumber,
                "Tu pedido ha sido entregado en el mostrador. ¡Gracias por entrenar con GYM VIP!");

        return getOrderByIdInternal(orderId);
    }

    // =========================================================================
    // 7. CANCELACIÓN Y LIBERACIÓN DE STOCK EXPIRADO
    // =========================================================================

    public OrderDetailDto cancelOrder(String userEmail, UUID orderId, String reason) {
        UUID accountId = getAccountIdByEmail(userEmail);

        var order = jdbc.query("""
            SELECT id, order_number, order_status, payment_status, stock_reserved, stock_released
            FROM supplement_orders
            WHERE id = ? AND account_id = ?
            FOR UPDATE
        """, (rs, n) -> Map.of(
                "order_number", rs.getString("order_number"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "stock_reserved", rs.getBoolean("stock_reserved"),
                "stock_released", rs.getBoolean("stock_released")
        ), orderId, accountId);

        if (order.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        var data = order.get(0);
        String currentStatus = (String) data.get("order_status");
        String paymentStatus = (String) data.get("payment_status");
        boolean stockReserved = (boolean) data.get("stock_reserved");
        boolean stockReleased = (boolean) data.get("stock_released");

        if ("VERIFICADO".equalsIgnoreCase(paymentStatus) || "ENTREGADO".equalsIgnoreCase(currentStatus)) {
            throw new ConflictException("No se puede cancelar un pedido con pago verificado o entregado.");
        }

        if (stockReserved && !stockReleased) {
            releaseStockForOrder(orderId, (String) data.get("order_number"), userEmail);
        }

        jdbc.update("""
            UPDATE supplement_orders
            SET order_status = 'CANCELADO', fulfillment_status = 'ANULADO', cancellation_reason = ?,
                stock_released = true, updated_at_utc = ?
            WHERE id = ?
        """, reason.trim(), now(), orderId);

        recordOrderAudit(orderId, "ORDER_CANCELLED", currentStatus, "CANCELADO", userEmail, "Cancelado por el usuario: " + reason.trim());

        return getOrderByIdInternal(orderId);
    }

    private void releaseStockForOrder(UUID orderId, String orderNumber, String actor) {
        var items = jdbc.query("""
            SELECT supplement_id, quantity FROM supplement_order_items WHERE order_id = ?
        """, (rs, n) -> Map.of(
                "supplement_id", rs.getObject("supplement_id", UUID.class),
                "quantity", rs.getInt("quantity")
        ), orderId);

        for (var it : items) {
            UUID supId = (UUID) it.get("supplement_id");
            int qty = (int) it.get("quantity");

            var sup = jdbc.query("SELECT stock FROM supplements WHERE id = ? FOR UPDATE", (rs, n) -> rs.getInt("stock"), supId);
            if (!sup.isEmpty()) {
                int prevStock = sup.get(0);
                int newStock = prevStock + qty;
                jdbc.update("UPDATE supplements SET stock = ?, availability = 'DISPONIBLE', updated_at_utc = ? WHERE id = ?",
                        newStock, now(), supId);

                jdbc.update("""
                    INSERT INTO supplement_stock_movements (id, supplement_id, movement_type, quantity, previous_stock, new_stock, reason, actor, created_at_utc)
                    VALUES (?, ?, 'ENTRADA', ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), supId, qty, prevStock, newStock, "LIBERACION_STOCK_" + orderNumber, actor, now());
            }
        }
    }

    public int releaseExpiredReservations() {
        var expiredOrders = jdbc.query("""
            SELECT id, order_number FROM supplement_orders
            WHERE stock_reserved = true AND stock_released = false
              AND payment_status = 'PENDIENTE'
              AND reservation_expires_at_utc < ?
            FOR UPDATE SKIP LOCKED
        """, (rs, n) -> Map.of(
                "id", rs.getObject("id", UUID.class),
                "order_number", rs.getString("order_number")
        ), now());

        int count = 0;
        for (var ord : expiredOrders) {
            UUID ordId = (UUID) ord.get("id");
            String ordNum = (String) ord.get("order_number");
            releaseStockForOrder(ordId, ordNum, "SISTEMA_RESERVA_EXPIRADA");
            jdbc.update("""
                UPDATE supplement_orders
                SET order_status = 'EXPIRADO', stock_released = true, updated_at_utc = ?
                WHERE id = ?
            """, now(), ordId);
            recordOrderAudit(ordId, "ORDER_EXPIRED", "PENDIENTE_PAGO", "EXPIRADO", "SISTEMA", "Reserva de 30 minutos vencida sin evidencia de pago.");
            count++;
        }
        return count;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void scheduledReleaseExpiredReservations() {
        try {
            releaseExpiredReservations();
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 8. CONSULTAS Y LISTADOS
    // =========================================================================

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderForClient(String userEmail, UUID orderId) {
        UUID accountId = getAccountIdByEmail(userEmail);
        var belongs = jdbc.query("SELECT 1 FROM supplement_orders WHERE id = ? AND account_id = ?", (rs, n) -> true, orderId, accountId);
        if (belongs.isEmpty()) {
            throw new ResourceNotFoundException("Pedido no encontrado.");
        }
        return getOrderByIdInternal(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderDetailDto> listOrdersForClient(String userEmail) {
        UUID accountId = getAccountIdByEmail(userEmail);
        return jdbc.query("""
            SELECT id FROM supplement_orders WHERE account_id = ? ORDER BY created_at_utc DESC
        """, (rs, n) -> getOrderByIdInternal(rs.getObject("id", UUID.class)), accountId);
    }

    @Transactional(readOnly = true)
    public List<OrderDetailDto> listOrdersForAdmin(String statusFilter) {
        String sql = """
            SELECT id FROM supplement_orders
        """;
        if (statusFilter != null && !statusFilter.isBlank() && !"TODOS".equalsIgnoreCase(statusFilter)) {
            sql += " WHERE order_status = '" + statusFilter.trim().toUpperCase() + "'";
        }
        sql += " ORDER BY created_at_utc DESC LIMIT 100";
        return jdbc.query(sql, (rs, n) -> getOrderByIdInternal(rs.getObject("id", UUID.class)));
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderById(UUID orderId) {
        return getOrderByIdInternal(orderId);
    }

    private OrderDetailDto getOrderByIdInternal(UUID orderId) {
        var rows = jdbc.query("""
            SELECT o.id, o.order_number, o.client_id, c.first_name, c.last_name, c.member_code, c.phone,
                   o.order_status, o.payment_status, o.fulfillment_status,
                   o.subtotal_pen, o.discount_pen, o.total_pen, o.payment_method, o.payment_reference,
                   o.payment_evidence_url, o.payment_evidence_notes, o.payment_evidence_at_utc,
                   o.verified_by, o.verified_at_utc, o.verified_amount_pen, o.rejection_reason,
                   o.cancellation_reason, o.reservation_expires_at_utc, o.stock_reserved,
                   o.pickup_token IS NOT NULL AS has_pickup_token, o.fulfilled_by, o.fulfilled_at_utc,
                   o.customer_notes, o.created_at_utc
            FROM supplement_orders o
            JOIN clients c ON c.id = o.client_id
            WHERE o.id = ?
        """, (rs, n) -> rs, orderId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado.");

        try {
            var r = rows.get(0);
            List<OrderItemDto> items = getOrderItems(orderId);
            String clientName = r.getString("first_name") + " " + r.getString("last_name");
            String memberCode = r.getString("member_code");

            return new OrderDetailDto(
                    r.getObject("id", UUID.class),
                    r.getString("order_number"),
                    r.getObject("client_id", UUID.class),
                    clientName,
                    "CÓDIGO: " + memberCode,
                    r.getString("phone"),
                    r.getString("order_status"),
                    r.getString("payment_status"),
                    r.getString("fulfillment_status"),
                    r.getBigDecimal("subtotal_pen"),
                    r.getBigDecimal("discount_pen"),
                    r.getBigDecimal("total_pen"),
                    r.getString("payment_method"),
                    r.getString("payment_reference"),
                    r.getString("payment_evidence_url"),
                    r.getString("payment_evidence_notes"),
                    r.getTimestamp("payment_evidence_at_utc") != null ? r.getTimestamp("payment_evidence_at_utc").toInstant().toString() : null,
                    r.getString("verified_by"),
                    r.getTimestamp("verified_at_utc") != null ? r.getTimestamp("verified_at_utc").toInstant().toString() : null,
                    r.getBigDecimal("verified_amount_pen"),
                    r.getString("rejection_reason"),
                    r.getString("cancellation_reason"),
                    r.getTimestamp("reservation_expires_at_utc") != null ? r.getTimestamp("reservation_expires_at_utc").toInstant().toString() : null,
                    r.getBoolean("stock_reserved"),
                    r.getBoolean("has_pickup_token"),
                    r.getString("fulfilled_by"),
                    r.getTimestamp("fulfilled_at_utc") != null ? r.getTimestamp("fulfilled_at_utc").toInstant().toString() : null,
                    r.getString("customer_notes"),
                    r.getTimestamp("created_at_utc") != null ? r.getTimestamp("created_at_utc").toInstant().toString() : now().toString(),
                    items
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<OrderItemDto> getOrderItems(UUID orderId) {
        return jdbc.query("""
            SELECT id, supplement_id, product_name, product_brand, product_presentation, product_flavor,
                   unit_regular_price_pen, unit_final_price_pen, applied_promotion_name, quantity, line_total_pen
            FROM supplement_order_items
            WHERE order_id = ?
            ORDER BY product_name ASC
        """, (rs, n) -> new OrderItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("supplement_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("product_brand"),
                rs.getString("product_presentation"),
                rs.getString("product_flavor"),
                rs.getBigDecimal("unit_regular_price_pen"),
                rs.getBigDecimal("unit_final_price_pen"),
                rs.getString("applied_promotion_name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_total_pen")
        ), orderId);
    }

    private void recordOrderAudit(UUID orderId, String action, String fromStatus, String toStatus, String actor, String details) {
        jdbc.update("""
            INSERT INTO supplement_order_audit (id, order_id, action, from_order_status, to_order_status, actor, details, created_at_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, UUID.randomUUID(), orderId, action, fromStatus, toStatus, actor, details, now());
    }

    private void queueOutboxPush(UUID clientId, String title, String message) {
        try {
            String idempotencyKey = "order_notice_" + UUID.randomUUID();
            jdbc.update("""
                INSERT INTO notification_outbox (id, idempotency_key, channel, recipient, title, payload, status)
                VALUES (?, ?, 'PUSH', ?, ?, ?, 'PENDING')
                ON CONFLICT (idempotency_key) DO NOTHING
            """, UUID.randomUUID(), idempotencyKey, clientId.toString(), title, message);
        } catch (Exception ignored) {}
    }
}
