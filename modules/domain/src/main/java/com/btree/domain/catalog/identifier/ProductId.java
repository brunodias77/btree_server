package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class ProductId extends Identifier {

    private final UUID value;

    private ProductId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'ProductId value' must not be null");
    }

    public static ProductId unique() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId from(final UUID value) {
        return new ProductId(value);
    }

    public static ProductId from(final String value) {
        return new ProductId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
