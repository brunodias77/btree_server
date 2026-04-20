package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class StockMovementError {

    public static final Error MOVEMENT_NOT_FOUND = new Error("Movimentação de estoque não encontrada");

    // Validator errors
    public static final Error QUANTITY_ZERO = new Error("'quantity' deve ser diferente de zero");
    public static final Error MOVEMENT_TYPE_NULL = new Error("'movementType' não pode ser nulo");
    public static final Error PRODUCT_ID_NULL = new Error("'productId' não pode ser nulo");

    // Business rule errors
    public static final Error INSUFFICIENT_STOCK = new Error("Estoque insuficiente para realizar a operação");

    private StockMovementError() {}
}
