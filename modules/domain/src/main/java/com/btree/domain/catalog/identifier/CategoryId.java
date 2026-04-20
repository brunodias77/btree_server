package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class CategoryId extends Identifier {

    private final UUID value;

    private CategoryId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'CategoryId value' must not be null");
    }


    public static CategoryId unique() {
        return new CategoryId(UUID.randomUUID());
    }

    public static CategoryId from(final UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId from(final String value) {
        return new CategoryId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
