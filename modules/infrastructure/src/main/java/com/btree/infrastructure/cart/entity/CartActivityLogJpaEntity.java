package com.btree.infrastructure.cart.entity;
import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.identifier.CartActivityLogId;
import com.btree.domain.cart.identifier.CartId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code cart.cart_activity_log}.
 * Immutable audit log. Child of Cart — ON DELETE CASCADE.
 *
 * <p>{@code details} column is JSONB; stored as a raw {@code String} here
 * and interpreted by the application layer as needed.
 */
@Entity
@Table(name = "cart_activity_log", schema = "cart")
public class CartActivityLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "details", columnDefinition = "JSONB")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CartActivityLogJpaEntity() {}

    public static CartActivityLogJpaEntity from(final CartActivityLog log) {
        final var entity = new CartActivityLogJpaEntity();
        entity.id = log.getId().getValue();
        entity.cartId = log.getCartId().getValue();
        entity.action = log.getAction();
        entity.productId = log.getProductId();
        entity.quantity = log.getQuantity();
        entity.details = log.getDetails();
        entity.createdAt = log.getCreatedAt();
        return entity;
    }

    public CartActivityLog toAggregate() {
        return CartActivityLog.with(
                CartActivityLogId.from(this.id),
                CartId.from(this.cartId),
                this.action,
                this.productId,
                this.quantity,
                this.details,
                this.createdAt
        );
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
