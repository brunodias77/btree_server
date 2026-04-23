package com.btree.domain.cart.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class CartItemId extends Identifier {

    private final UUID value;

    private CartItemId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'CartItemId value' must not be null");
    }

    public static CartItemId unique() {
        return new CartItemId(UUID.randomUUID());
    }

    public static CartItemId from(final UUID value) {
        return new CartItemId(value);
    }

    public static CartItemId from(final String value) {
        return new CartItemId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
