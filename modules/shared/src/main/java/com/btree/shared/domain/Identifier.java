package com.btree.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for typed identifiers — wraps a UUID to provide type-safe identity.
 * <p>
 * Mapped to schema pattern: {@code id UUID PRIMARY KEY DEFAULT uuid_generate_v7()}
 * <p>
 * Usage: Create specific identifiers like {@code UserId}, {@code ProductId}, etc.
 * This prevents accidentally passing a UserId where a ProductId is expected.
 */
public abstract class Identifier extends ValueObject {

    public abstract UUID getValue();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identifier that = (Identifier) o;
        return Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue());
    }

    @Override
    public String toString() {
        return getValue() != null ? getValue().toString() : "null";
    }
}
