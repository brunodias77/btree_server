package com.btree.infrastructure.catalog.entity;

import com.btree.domain.catalog.entity.UserFavorite;
import com.btree.domain.catalog.identifier.ProductId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.user_favorites}.
 * Composite primary key: {@code (user_id, product_id)} via {@link UserFavoritePk}.
 */
@Entity
@Table(name = "user_favorites", schema = "catalog")
public class UserFavoriteJpaEntity {

    @EmbeddedId
    private UserFavoritePk id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserFavoriteJpaEntity() {}

    public static UserFavoriteJpaEntity from(final UserFavorite favorite) {
        final var entity = new UserFavoriteJpaEntity();
        entity.id = new UserFavoritePk(favorite.getUserId(), favorite.getProductId().getValue());
        entity.createdAt = favorite.getCreatedAt();
        return entity;
    }

    public UserFavorite toAggregate() {
        return UserFavorite.with(
                this.id.getUserId(),
                ProductId.from(this.id.getProductId()),
                this.createdAt
        );
    }

    // ── Composite PK ─────────────────────────────────────────

    @Embeddable
    public static class UserFavoritePk implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        public UserFavoritePk() {}

        public UserFavoritePk(final UUID userId, final UUID productId) {
            this.userId = userId;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final UserFavoritePk that = (UserFavoritePk) o;
            return Objects.equals(userId, that.userId) && Objects.equals(productId, that.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, productId);
        }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
    }

    // ── Getters / Setters ────────────────────────────────────

    public UserFavoritePk getId() { return id; }
    public void setId(UserFavoritePk id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
