package com.btree.domain.coupon.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class CouponId extends Identifier {

    private final UUID value;

    private CouponId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'CouponId' não deve ser nulo");
    }

    public static CouponId unique() {
        return new CouponId(UUID.randomUUID());
    }

    public static CouponId from(final UUID value) {
        return new CouponId(value);
    }

    public static CouponId from(final String value) {
        return new CouponId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
