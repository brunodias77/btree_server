package com.btree.domain.cart.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class SavedCartId extends Identifier {

    private final UUID value;

    private SavedCartId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'SavedCartId value' must not be null");
    }

    public static SavedCartId unique() {
        return new SavedCartId(UUID.randomUUID());
    }

    public static SavedCartId from(final UUID value) {
        return new SavedCartId(value);
    }

    public static SavedCartId from(final String value) {
        return new SavedCartId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}

