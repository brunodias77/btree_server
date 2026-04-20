package com.btree.domain.catalog.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class ProductReviewId extends Identifier {

    private final UUID value;

    private ProductReviewId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'ProductReviewId value' must not be null");
    }

    public static ProductReviewId unique() {
        return new ProductReviewId(UUID.randomUUID());
    }

    public static ProductReviewId from(final UUID value) {
        return new ProductReviewId(value);
    }

    public static ProductReviewId from(final String value) {
        return new ProductReviewId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
