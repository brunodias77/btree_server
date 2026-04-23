package com.btree.domain.cart.error;

import com.btree.shared.validation.Error;

public class CartActivityLogError {

    public static final Error LOG_NOT_FOUND    = new Error("Registro de atividade do carrinho não encontrado");
    public static final Error ACTION_EMPTY     = new Error("'action' não pode estar vazio");
    public static final Error CART_ID_NULL     = new Error("'cartId' não pode ser nulo");

    private CartActivityLogError() {}
}
