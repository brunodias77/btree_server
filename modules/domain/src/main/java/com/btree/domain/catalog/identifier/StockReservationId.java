package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class StockReservationId extends Identifier {

    private final UUID value;

    private StockReservationId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'StockReservationId value' must not be null");
    }

    public static StockReservationId unique() {
        return new StockReservationId(UUID.randomUUID());
    }

    public static StockReservationId from(final UUID value) {
        return new StockReservationId(value);
    }

    public static StockReservationId from(final String value) {
        return new StockReservationId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
