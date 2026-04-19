package com.btree.domain.catalog.value_object;

import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object representing a Stock Keeping Unit (SKU).
 *
 * <p>Maps to {@code catalog.products.sku} — VARCHAR(50) UNIQUE NOT NULL.
 * Allows uppercase alphanumeric characters, hyphens, and underscores.
 */
public class Sku extends ValueObject {

    private static final int MAX_LENGTH = 50;
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final String value;

    private Sku(final String value) {
        Objects.requireNonNull(value, "'sku' não pode ser nulo");
        final String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("'sku' não pode ser vazio");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("'sku' não pode exceder %d caracteres".formatted(MAX_LENGTH));
        }
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("'sku' contém caracteres inválidos — use apenas letras maiúsculas, números, hífens e underscores");
        }
        this.value = normalized;
    }

    public static Sku of(final String value) {
        return new Sku(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Sku sku = (Sku) o;
        return value.equals(sku.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

