package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.identifier.ProductId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Association object — maps to {@code catalog.user_favorites} table.
 *
 * <p>Composite primary key: (user_id, product_id). Not an Entity in the DDD sense
 * since it has no UUID identity — it is identified by the combination of user and product.
 */
public class UserFavorite {

    private final UUID userId;
    private final ProductId productId;
    private final Instant createdAt;

    private UserFavorite(final UUID userId, final ProductId productId, final Instant createdAt) {
        this.userId = Objects.requireNonNull(userId, "'userId' não pode ser nulo");
        this.productId = Objects.requireNonNull(productId, "'productId' não pode ser nulo");
        this.createdAt = Objects.requireNonNull(createdAt, "'createdAt' não pode ser nulo");
    }

    public static UserFavorite create(final UUID userId, final ProductId productId) {
        return new UserFavorite(userId, productId, Instant.now());
    }

    public static UserFavorite with(final UUID userId, final ProductId productId, final Instant createdAt) {
        return new UserFavorite(userId, productId, createdAt);
    }

    public UUID getUserId() { return userId; }
    public ProductId getProductId() { return productId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserFavorite that = (UserFavorite) o;
        return userId.equals(that.userId) && productId.equals(that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, productId);
    }
}
