package com.btree.domain.coupon.validator;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.error.CouponError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

import java.math.BigDecimal;

public class CouponValidator extends Validator {

    private final Coupon coupon;

    public CouponValidator(final Coupon coupon, final ValidationHandler handler) {
        super(handler);
        this.coupon = coupon;
    }

    @Override
    public void validate() {
        checkDiscountValue();
        checkStartsAt();
        checkDates();
        checkMaxUsesPerUser();
    }

    private void checkDiscountValue() {
        final var value = coupon.getDiscountValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            this.validationHandler().append(CouponError.DISCOUNT_VALUE_INVALIDO);
        }
    }

    private void checkStartsAt() {
        if (coupon.getStartsAt() == null) {
            this.validationHandler().append(CouponError.STARTS_AT_NULL);
        }
    }

    private void checkDates() {
        if (coupon.getStartsAt() == null || coupon.getExpiresAt() == null) {
            return;
        }
        if (!coupon.getExpiresAt().isAfter(coupon.getStartsAt())) {
            this.validationHandler().append(CouponError.EXPIRES_AT_INVALIDO);
        }
    }

    private void checkMaxUsesPerUser() {
        if (coupon.getMaxUsesPerUser() < 1) {
            this.validationHandler().append(CouponError.MAX_USES_PER_USER_INVALIDO);
        }
    }
}
