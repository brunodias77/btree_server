package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class StockMovementId extends Identifier {

    private final UUID value;

    private StockMovementId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'StockMovementId value' must not be null");
    }

    public static StockMovementId unique() {
        return new StockMovementId(UUID.randomUUID());
    }

    public static StockMovementId from(final UUID value) {
        return new StockMovementId(value);
    }

    public static StockMovementId from(final String value) {
        return new StockMovementId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
