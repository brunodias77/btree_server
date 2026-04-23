package com.btree.domain.cart.error;

import com.btree.shared.validation.Error;

public class SavedCartError {

    public static final Error SAVED_CART_NOT_FOUND = new Error("Lista de compras salva não encontrada");

    // Validator errors
    public static final Error NAME_EMPTY           = new Error("'name' não pode estar vazio");
    public static final Error NAME_TOO_LONG        = new Error("'name' deve ter no máximo 100 caracteres");
    public static final Error USER_ID_NULL         = new Error("'userId' não pode ser nulo");
    public static final Error ITEMS_EMPTY          = new Error("A lista de compras salva não pode estar vazia");

    private SavedCartError() {}
}
