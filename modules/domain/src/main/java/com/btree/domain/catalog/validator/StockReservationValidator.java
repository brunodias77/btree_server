package com.btree.domain.catalog.validator;

import com.btree.domain.catalog.entity.StockReservation;
import com.btree.domain.catalog.error.StockReservationError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

import java.time.Instant;

/**
 * Validador das invariantes da entidade StockReservation.
 */
public class StockReservationValidator extends Validator {

    private final StockReservation reservation;

    public StockReservationValidator(final StockReservation reservation, final ValidationHandler handler) {
        super(handler);
        this.reservation = reservation;
    }

    @Override
    public void validate() {
        checkProductId();
        checkQuantity();
        checkExpiresAt();
    }

    private void checkProductId() {
        if (this.reservation.getProductId() == null) {
            validationHandler().append(StockReservationError.PRODUCT_ID_NULL);
        }
    }

    private void checkQuantity() {
        if (this.reservation.getQuantity() <= 0) {
            validationHandler().append(StockReservationError.QUANTITY_NOT_POSITIVE);
        }
    }

    private void checkExpiresAt() {
        final var expiresAt = this.reservation.getExpiresAt();
        if (expiresAt == null) {
            validationHandler().append(StockReservationError.EXPIRES_AT_NULL);
            return;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            validationHandler().append(StockReservationError.EXPIRES_AT_PAST);
        }
    }
}
