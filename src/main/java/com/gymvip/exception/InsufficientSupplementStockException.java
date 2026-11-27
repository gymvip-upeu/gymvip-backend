package com.gymvip.exception;

import java.util.UUID;

public class InsufficientSupplementStockException extends ConflictException {
    public record Stock(UUID supplementId, int available, int requested) {}

    private final Stock stock;

    public InsufficientSupplementStockException(UUID id, String name, int available, int requested) {
        super("Stock insuficiente para «" + name + "». Disponibles: " + available + ".");
        stock = new Stock(id, available, requested);
    }

    public Stock getStock() {
        return stock;
    }
}
