package com.btree.domain.cart.error;

import com.btree.shared.validation.Error;

public class CartError {

    public static final Error CART_NOT_FOUND          = new Error("Carrinho não encontrado");
    public static final Error INVALID_IDENTIFICATION  = new Error("'userId' ou 'sessionId' é obrigatório");
    public static final Error INVALID_USER_ID         = new Error("'userId' não é um UUID válido");
    public static final Error CART_NOT_ACTIVE        = new Error("Carrinho não está ativo");
    public static final Error CART_ALREADY_CONVERTED = new Error("Carrinho já foi convertido em pedido");
    public static final Error CART_ALREADY_ABANDONED = new Error("Carrinho já foi abandonado");
    public static final Error CART_ALREADY_EXPIRED   = new Error("Carrinho já expirou");
    public static final Error CART_EMPTY             = new Error("Carrinho não possui itens");

    // Coupon errors
    public static final Error COUPON_ALREADY_APPLIED = new Error("Um cupom já está aplicado ao carrinho");
    public static final Error NO_COUPON_APPLIED       = new Error("Nenhum cupom está aplicado ao carrinho");

    // Shipping errors
    public static final Error SHIPPING_METHOD_NULL   = new Error("'shippingMethod' não pode ser nulo");

    // Validator errors
    public static final Error STATUS_NULL            = new Error("'status' não pode ser nulo");

    private CartError() {}
}
