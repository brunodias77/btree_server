package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class StockReservationError {

    public static final Error RESERVATION_NOT_FOUND = new Error("Reserva de estoque não encontrada");

    // Validator errors
    public static final Error QUANTITY_NOT_POSITIVE = new Error("'quantity' deve ser maior que zero");
    public static final Error EXPIRES_AT_NULL = new Error("'expiresAt' não pode ser nulo");
    public static final Error EXPIRES_AT_PAST = new Error("'expiresAt' deve ser uma data futura");
    public static final Error PRODUCT_ID_NULL = new Error("'productId' não pode ser nulo");

    // Business rule errors
    public static final Error RESERVATION_ALREADY_CONFIRMED = new Error("Reserva já foi confirmada");
    public static final Error RESERVATION_ALREADY_RELEASED = new Error("Reserva já foi liberada");
    public static final Error RESERVATION_EXPIRED = new Error("Reserva de estoque expirada");
    public static final Error INSUFFICIENT_STOCK = new Error("Estoque insuficiente para realizar a reserva");

    private StockReservationError() {}
}
