package com.gymvip.service;

import com.gymvip.dto.supplement.SupplementStockDtos.*;
import com.gymvip.exception.ConflictException;
import com.gymvip.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SupplementStockService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    public SupplementStockService(JdbcClient jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SupplementStockSummaryDto getStockSummary(UUID supplementId) {
        return jdbc.sql("""
                SELECT id, name, brand, presentation, stock, min_stock, availability
                FROM supplements
                WHERE id = :id
                """)
                .param("id", supplementId)
                .query((rs, rowNum) -> {
                    int stock = rs.getInt("stock");
                    int minStock = rs.getInt("min_stock");
                    return new SupplementStockSummaryDto(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("brand"),
                            rs.getString("presentation"),
                            stock,
                            minStock,
                            stock > 0 && stock <= minStock,
                            stock <= 0,
                            rs.getString("availability")
                    );
                })
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Suplemento no encontrado."));
    }

    @Transactional(readOnly = true)
    public List<SupplementStockSummaryDto> listStockAlerts() {
        return jdbc.sql("""
                SELECT id, name, brand, presentation, stock, min_stock, availability
                FROM supplements
                WHERE published = true AND stock <= min_stock
                ORDER BY stock ASC, name ASC
                """)
                .query((rs, rowNum) -> {
                    int stock = rs.getInt("stock");
                    int minStock = rs.getInt("min_stock");
                    return new SupplementStockSummaryDto(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("brand"),
                            rs.getString("presentation"),
                            stock,
                            minStock,
                            stock > 0 && stock <= minStock,
                            stock <= 0,
                            rs.getString("availability")
                    );
                })
                .list();
    }

    public StockMovementDto recordMovement(UUID supplementId, StockMovementRequest req, String actor) {
        Instant now = clock.instant();

        // 1. Bloqueo pesimista para concurrencia estricta (FASE 10)
        var row = jdbc.sql("""
                SELECT stock, min_stock, availability, version
                FROM supplements
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", supplementId)
                .query((rs, rowNum) -> new int[]{
                        rs.getInt("stock"),
                        rs.getInt("min_stock"),
                        (int) rs.getLong("version")
                })
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Suplemento no encontrado."));

        int previousStock = row[0];
        int movementQty = req.quantity();
        int newStock;

        switch (req.movementType().toUpperCase()) {
            case "ENTRADA" -> newStock = previousStock + movementQty;
            case "SALIDA" -> {
                if (movementQty > previousStock) {
                    throw new ConflictException("Stock insuficiente para realizar la salida solicitada. Existencias disponibles: " + previousStock);
                }
                newStock = previousStock - movementQty;
            }
            case "AJUSTE" -> newStock = movementQty;
            default -> throw new IllegalArgumentException("Tipo de movimiento no soportado.");
        }

        String newAvailability = newStock > 0 ? "DISPONIBLE" : "AGOTADO";

        // 2. Actualizar stock y versión
        jdbc.sql("""
                UPDATE supplements
                SET stock = :stock,
                    availability = :avail,
                    version = version + 1,
                    updated_at_utc = :now
                WHERE id = :id
                """)
                .param("stock", newStock)
                .param("avail", newAvailability)
                .param("now", java.sql.Timestamp.from(now))
                .param("id", supplementId)
                .update();

        // 3. Registrar movimiento en kardex/historial
        UUID movementId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO supplement_stock_movements (
                    id, supplement_id, movement_type, quantity, previous_stock, new_stock, reason, actor, created_at_utc
                ) VALUES (
                    :id, :supId, :type, :qty, :prev, :newStock, :reason, :actor, :now
                )
                """)
                .param("id", movementId)
                .param("supId", supplementId)
                .param("type", req.movementType().toUpperCase())
                .param("qty", movementQty)
                .param("prev", previousStock)
                .param("newStock", newStock)
                .param("reason", req.reason().trim())
                .param("actor", actor)
                .param("now", java.sql.Timestamp.from(now))
                .update();

        // 4. Auditoría de seguridad y trazabilidad
        audit.recordEvent(actor, "SUPPLEMENT_STOCK_MOVEMENT", "Supplement", supplementId.toString(),
                req.movementType() + " de " + movementQty + " unidades. Stock resultante: " + newStock + " (" + req.reason().trim() + ")");

        return new StockMovementDto(
                movementId, supplementId, req.movementType().toUpperCase(), movementQty,
                previousStock, newStock, req.reason().trim(), actor, now
        );
    }

    @Transactional(readOnly = true)
    public List<StockMovementDto> listMovements(UUID supplementId) {
        return jdbc.sql("""
                SELECT id, supplement_id, movement_type, quantity, previous_stock, new_stock, reason, actor, created_at_utc
                FROM supplement_stock_movements
                WHERE supplement_id = :id
                ORDER BY created_at_utc DESC
                LIMIT 100
                """)
                .param("id", supplementId)
                .query((rs, rowNum) -> new StockMovementDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("supplement_id", UUID.class),
                        rs.getString("movement_type"),
                        rs.getInt("quantity"),
                        rs.getInt("previous_stock"),
                        rs.getInt("new_stock"),
                        rs.getString("reason"),
                        rs.getString("actor"),
                        rs.getTimestamp("created_at_utc").toInstant()
                ))
                .list();
    }
}
