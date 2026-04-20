package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class ProductImageId extends Identifier {

    private final UUID value;

    private ProductImageId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'ProductImageId value' must not be null");
    }

    public static ProductImageId unique() {
        return new ProductImageId(UUID.randomUUID());
    }

    public static ProductImageId from(final UUID value) {
        return new ProductImageId(value);
    }

    public static ProductImageId from(final String value) {
        return new ProductImageId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
