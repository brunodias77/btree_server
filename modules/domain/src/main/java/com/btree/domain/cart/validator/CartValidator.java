package com.btree.domain.cart.validator;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.error.CartError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

/**
 * Validador das invariantes do agregado Cart.
 */
public class CartValidator extends Validator {

    private final Cart cart;

    public CartValidator(final Cart cart, final ValidationHandler handler) {
        super(handler);
        this.cart = cart;
    }

    @Override
    public void validate() {
        checkStatus();
        checkIdentity();
    }

    private void checkStatus() {
        if (this.cart.getStatus() == null) {
            validationHandler().append(CartError.STATUS_NULL);
        }
    }

    /**
     * Um carrinho precisa ter ao menos um identificador de dono:
     * userId (autenticado) ou sessionId (guest). Ambos podem coexistir
     * após a associação do carrinho guest ao usuário autenticado.
     */
    private void checkIdentity() {
        final boolean hasUser    = this.cart.getUserId() != null;
        final boolean hasSession = this.cart.getSessionId() != null && !this.cart.getSessionId().isBlank();
        if (!hasUser && !hasSession) {
            validationHandler().append(new com.btree.shared.validation.Error(
                    "O carrinho deve pertencer a um usuário ou a uma sessão anônima"
            ));
        }
    }
}

