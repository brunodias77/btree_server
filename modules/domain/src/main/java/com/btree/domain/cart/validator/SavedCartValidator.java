package com.btree.domain.cart.validator;


import com.btree.domain.cart.entity.SavedCart;
import com.btree.domain.cart.error.SavedCartError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

/**
 * Validador das invariantes do agregado SavedCart.
 */
public class SavedCartValidator extends Validator {

    private static final int NAME_MAX_LENGTH = 100;

    private final SavedCart savedCart;

    public SavedCartValidator(final SavedCart savedCart, final ValidationHandler handler) {
        super(handler);
        this.savedCart = savedCart;
    }

    @Override
    public void validate() {
        checkUserId();
        checkName();
        checkItems();
    }

    private void checkUserId() {
        if (this.savedCart.getUserId() == null) {
            validationHandler().append(SavedCartError.USER_ID_NULL);
        }
    }

    private void checkName() {
        final var name = this.savedCart.getName();
        if (name == null || name.isBlank()) {
            validationHandler().append(SavedCartError.NAME_EMPTY);
            return;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            validationHandler().append(SavedCartError.NAME_TOO_LONG);
        }
    }

    private void checkItems() {
        if (this.savedCart.getItems() == null || this.savedCart.getItems().isEmpty()) {
            validationHandler().append(SavedCartError.ITEMS_EMPTY);
        }
    }
}

