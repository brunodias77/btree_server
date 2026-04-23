package com.btree.domain.cart.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class CartActivityLogId extends Identifier {

    private final UUID value;

    private CartActivityLogId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'CartActivityLogId value' must not be null");
    }

    public static CartActivityLogId unique() {
        return new CartActivityLogId(UUID.randomUUID());
    }

    public static CartActivityLogId from(final UUID value) {
        return new CartActivityLogId(value);
    }

    public static CartActivityLogId from(final String value) {
        return new CartActivityLogId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}

