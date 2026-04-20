package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockMovementId;
import com.btree.shared.domain.Entity;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity — maps to {@code catalog.stock_movements} table (partitioned by quarter).
 *
 * <p>Immutable after creation — represents a ledger entry for stock changes.
 * Positive quantity = stock in; negative quantity = stock out.
 */
public class StockMovement extends Entity<StockMovementId> {

    private final Instant createdAt;
    private final ProductId productId;
    private final StockMovementType movementType;
    private final int quantity;
    private final UUID referenceId;
    private final String referenceType;
    private final String notes;

    private StockMovement(
            final StockMovementId id,
            final Instant createdAt,
            final ProductId productId,
            final StockMovementType movementType,
            final int quantity,
            final UUID referenceId,
            final String referenceType,
            final String notes
    ) {
        super(id);
        this.createdAt = createdAt;
        this.productId = productId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.notes = notes;
    }

    public static StockMovement create(
            final ProductId productId,
            final StockMovementType movementType,
            final int quantity,
            final UUID referenceId,
            final String referenceType,
            final String notes
    ) {
        return new StockMovement(
                StockMovementId.unique(),
                Instant.now(),
                productId, movementType, quantity,
                referenceId, referenceType, notes
        );
    }

    public static StockMovement with(
            final StockMovementId id,
            final Instant createdAt,
            final ProductId productId,
            final StockMovementType movementType,
            final int quantity,
            final UUID referenceId,
            final String referenceType,
            final String notes
    ) {
        return new StockMovement(id, createdAt, productId, movementType, quantity, referenceId, referenceType, notes);
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        // StockMovement is immutable — validation done at use case level before creation
    }

    // ── Getters ──────────────────────────────────────────────

    public Instant getCreatedAt() { return createdAt; }
    public ProductId getProductId() { return productId; }
    public StockMovementType getMovementType() { return movementType; }
    public int getQuantity() { return quantity; }
    public UUID getReferenceId() { return referenceId; }
    public String getReferenceType() { return referenceType; }
    public String getNotes() { return notes; }
}
