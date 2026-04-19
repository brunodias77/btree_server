package com.btree.domain.catalog.identifier;


import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class BrandId extends Identifier {

    private final UUID value;

    private BrandId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'BrandId value' must not be null");
    }

    public static BrandId unique() {
        return new BrandId(UUID.randomUUID());
    }

    public static BrandId from(final UUID value) {
        return new BrandId(value);
    }

    public static BrandId from(final String value) {
        return new BrandId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
