package com.btree.infrastructure.catalog.entity;

import com.btree.domain.catalog.entity.StockReservation;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockReservationId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.stock_reservations}.
 */
@Entity
@Table(name = "stock_reservations", schema = "catalog")
public class StockReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "released", nullable = false)
    private boolean released;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockReservationJpaEntity() {}

    public static StockReservationJpaEntity from(final StockReservation reservation) {
        final var entity = new StockReservationJpaEntity();
        entity.id = reservation.getId().getValue();
        entity.productId = reservation.getProductId().getValue();
        entity.orderId = reservation.getOrderId();
        entity.quantity = reservation.getQuantity();
        entity.expiresAt = reservation.getExpiresAt();
        entity.confirmed = reservation.isConfirmed();
        entity.released = reservation.isReleased();
        entity.createdAt = reservation.getCreatedAt();
        return entity;
    }

    public StockReservation toAggregate() {
        return StockReservation.with(
                StockReservationId.from(this.id),
                ProductId.from(this.productId),
                this.orderId,
                this.quantity,
                this.expiresAt,
                this.confirmed,
                this.released,
                this.createdAt
        );
    }

    public void updateFrom(final StockReservation reservation) {
        this.confirmed = reservation.isConfirmed();
        this.released = reservation.isReleased();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
