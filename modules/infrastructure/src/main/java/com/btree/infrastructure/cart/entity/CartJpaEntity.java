package com.btree.infrastructure.cart.entity;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.identifier.CartId;
import com.btree.shared.enums.CartStatus;
import com.btree.shared.enums.ShippingMethod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code cart.carts}.
 * Optimistic locking via {@code @Version}.
 * Owns {@link CartItemJpaEntity} via cascade.
 */
@Entity
@Table(name = "carts", schema = "cart")
public class CartJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_id", length = 256)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "shared.cart_status")
    private CartStatus status;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", columnDefinition = "shared.shipping_method")
    private ShippingMethod shippingMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItemJpaEntity> items = new ArrayList<>();

    public CartJpaEntity() {}

    public static CartJpaEntity from(final Cart cart) {
        final var entity = new CartJpaEntity();
        entity.id = cart.getId().getValue();
        entity.userId = cart.getUserId();
        entity.sessionId = cart.getSessionId();
        entity.status = cart.getStatus();
        entity.couponCode = cart.getCouponCode();
        entity.shippingMethod = cart.getShippingMethod();
        entity.notes = cart.getNotes();
        entity.createdAt = cart.getCreatedAt();
        entity.updatedAt = cart.getUpdatedAt();
        entity.expiresAt = cart.getExpiresAt();

        cart.getItems().forEach(item ->
                entity.items.add(CartItemJpaEntity.from(item, entity))
        );
        return entity;
    }

    public Cart toAggregate() {
        final var itemAggregates = this.items.stream()
                .map(CartItemJpaEntity::toAggregate)
                .toList();
        return Cart.with(
                CartId.from(this.id),
                this.userId,
                this.sessionId,
                this.status,
                this.couponCode,
                this.shippingMethod,
                this.notes,
                this.createdAt,
                this.updatedAt,
                this.expiresAt,
                this.version,
                itemAggregates
        );
    }

    public void updateFrom(final Cart cart) {
        this.userId = cart.getUserId();
        this.sessionId = cart.getSessionId();
        this.status = cart.getStatus();
        this.couponCode = cart.getCouponCode();
        this.shippingMethod = cart.getShippingMethod();
        this.notes = cart.getNotes();
        this.updatedAt = cart.getUpdatedAt();
        this.expiresAt = cart.getExpiresAt();

        // sync items via orphanRemoval
        this.items.clear();
        cart.getItems().forEach(item ->
                this.items.add(CartItemJpaEntity.from(item, this))
        );
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public CartStatus getStatus() { return status; }
    public void setStatus(CartStatus status) { this.status = status; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public ShippingMethod getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(ShippingMethod shippingMethod) { this.shippingMethod = shippingMethod; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public List<CartItemJpaEntity> getItems() { return items; }
    public void setItems(List<CartItemJpaEntity> items) { this.items = items; }
}
