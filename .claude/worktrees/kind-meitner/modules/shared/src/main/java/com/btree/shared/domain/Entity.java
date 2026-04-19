package com.btree.shared.domain;

import com.btree.shared.validation.ValidationHandler;

import java.util.Objects;

/**
 * Base class for Entities — objects with a unique identity that persists over time.
 * <p>
 * Mapped to schema patterns:
 * <ul>
 *     <li>{@code id UUID PRIMARY KEY DEFAULT uuid_generate_v7()} — identity</li>
 *     <li>{@code created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()} — audit</li>
 *     <li>{@code updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()} — audit</li>
 * </ul>
 *
 * @param <ID> the type of the entity identifier (e.g., UserId, ProductId)
 */
public abstract class Entity<ID extends Identifier> {

    protected final ID id;

    protected Entity(final ID id) {
        Objects.requireNonNull(id, "'id' must not be null");
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    /**
     * Each entity must define how to validate its own invariants.
     *
     * @param handler the validation handler to collect errors
     */
    public abstract void validate(ValidationHandler handler);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + "}";
    }
}
