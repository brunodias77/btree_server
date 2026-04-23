package com.btree.domain.cart.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class CartId extends Identifier {

    private final UUID value;

    private CartId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'CartId value' must not be null");
    }

    public static CartId unique() {
        return new CartId(UUID.randomUUID());
    }

    public static CartId from(final UUID value) {
        return new CartId(value);
    }

    public static CartId from(final String value) {
        return new CartId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
