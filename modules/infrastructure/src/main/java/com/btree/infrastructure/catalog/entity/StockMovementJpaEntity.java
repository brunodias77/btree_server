package com.btree.infrastructure.catalog.entity;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockMovementId;
import com.btree.shared.enums.StockMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.stock_movements} (partitioned by quarter).
 *
 * <p>The table uses a composite primary key {@code (id, created_at)} because
 * PostgreSQL requires the partition key to be part of the PK in range-partitioned tables.
 * We use {@link IdClass} to satisfy this constraint at the JPA level.
 *
 * <p>Stock movements are immutable ledger entries — no {@code updateFrom} method.
 */
@Entity
@Table(name = "stock_movements", schema = "catalog")
@IdClass(StockMovementJpaEntity.StockMovementPk.class)
public class StockMovementJpaEntity {

    @Id
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, columnDefinition = "shared.stock_movement_type")
    private StockMovementType movementType;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public StockMovementJpaEntity() {}

    public static StockMovementJpaEntity from(final StockMovement movement) {
        final var entity = new StockMovementJpaEntity();
        entity.id = movement.getId().getValue();
        entity.createdAt = movement.getCreatedAt();
        entity.productId = movement.getProductId().getValue();
        entity.movementType = movement.getMovementType();
        entity.quantity = movement.getQuantity();
        entity.referenceId = movement.getReferenceId();
        entity.referenceType = movement.getReferenceType();
        entity.notes = movement.getNotes();
        return entity;
    }

    public StockMovement toAggregate() {
        return StockMovement.with(
                StockMovementId.from(this.id),
                this.createdAt,
                ProductId.from(this.productId),
                this.movementType,
                this.quantity,
                this.referenceId,
                this.referenceType,
                this.notes
        );
    }

    // ── Composite PK ─────────────────────────────────────────

    public static class StockMovementPk implements Serializable {
        private UUID id;
        private Instant createdAt;

        public StockMovementPk() {}

        public StockMovementPk(final UUID id, final Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final StockMovementPk that = (StockMovementPk) o;
            return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, createdAt);
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public StockMovementType getMovementType() { return movementType; }
    public void setMovementType(StockMovementType movementType) { this.movementType = movementType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
