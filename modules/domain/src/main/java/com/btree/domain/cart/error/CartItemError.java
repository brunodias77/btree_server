package com.btree.domain.cart.error;

import com.btree.shared.validation.Error;

public class CartItemError {

    public static final Error ITEM_NOT_FOUND         = new Error("Item não encontrado no carrinho");
    public static final Error ITEM_ALREADY_IN_CART   = new Error("Produto já está no carrinho");

    // Validator errors
    public static final Error PRODUCT_ID_NULL        = new Error("'productId' não pode ser nulo");
    public static final Error QUANTITY_NOT_POSITIVE  = new Error("'quantity' deve ser maior que zero");
    public static final Error UNIT_PRICE_NULL        = new Error("'unitPrice' não pode ser nulo");
    public static final Error UNIT_PRICE_NEGATIVE    = new Error("'unitPrice' não pode ser negativo");

    private CartItemError() {}
}
