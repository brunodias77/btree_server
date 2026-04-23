package com.btree.domain.cart.validator;


import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.cart.error.CartItemError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

import java.math.BigDecimal;

/**
 * Validador das invariantes da entidade CartItem.
 */
public class CartItemValidator extends Validator {

    private final CartItem item;

    public CartItemValidator(final CartItem item, final ValidationHandler handler) {
        super(handler);
        this.item = item;
    }

    @Override
    public void validate() {
        checkProductId();
        checkQuantity();
        checkUnitPrice();
    }

    private void checkProductId() {
        if (this.item.getProductId() == null) {
            validationHandler().append(CartItemError.PRODUCT_ID_NULL);
        }
    }

    private void checkQuantity() {
        if (this.item.getQuantity() <= 0) {
            validationHandler().append(CartItemError.QUANTITY_NOT_POSITIVE);
        }
    }

    private void checkUnitPrice() {
        final var price = this.item.getUnitPrice();
        if (price == null) {
            validationHandler().append(CartItemError.UNIT_PRICE_NULL);
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            validationHandler().append(CartItemError.UNIT_PRICE_NEGATIVE);
        }
    }
}
