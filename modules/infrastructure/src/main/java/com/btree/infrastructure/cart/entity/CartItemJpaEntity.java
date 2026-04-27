package com.btree.infrastructure.cart.entity;

import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.cart.identifier.CartId;
import com.btree.domain.cart.identifier.CartItemId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code cart.cart_items}.
 * Child of {@link CartJpaEntity} — ON DELETE CASCADE.
 * UNIQUE constraint: (cart_id, product_id).
 */
@Entity
@Table(name = "cart_items", schema = "cart")
public class CartItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartJpaEntity cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CartItemJpaEntity() {}

    public static CartItemJpaEntity from(final CartItem item, final CartJpaEntity cartEntity) {
        final var entity = new CartItemJpaEntity();
        entity.id = item.getId().getValue();
        entity.cart = cartEntity;
        entity.productId = item.getProductId();
        entity.quantity = item.getQuantity();
        entity.unitPrice = item.getUnitPrice();
        entity.createdAt = item.getCreatedAt();
        entity.updatedAt = item.getUpdatedAt();
        return entity;
    }

    public CartItem toAggregate() {
        return CartItem.with(
                CartItemId.from(this.id),
                CartId.from(this.cart.getId()),
                this.productId,
                this.quantity,
                this.unitPrice,
                this.createdAt,
                this.updatedAt
        );
    }

    public void updateFrom(final CartItem item) {
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.updatedAt = item.getUpdatedAt();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CartJpaEntity getCart() { return cart; }
    public void setCart(CartJpaEntity cart) { this.cart = cart; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
