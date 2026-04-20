package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.events.StockConfirmedEvent;
import com.btree.domain.catalog.events.StockReleasedEvent;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockReservationId;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.UUID;

import com.btree.domain.catalog.error.StockReservationError;
import com.btree.domain.catalog.validator.StockReservationValidator;

/**
 * Aggregate Root — maps to {@code catalog.stock_reservations} table.
 *
 * <p>Represents a temporary hold on stock quantity during checkout.
 * Transitions: active → confirmed (order placed) or active → released (expired/cancelled).
 */
public class StockReservation extends AggregateRoot<StockReservationId> {

    private final ProductId productId;
    private final UUID orderId;
    private final int quantity;
    private final Instant expiresAt;
    private boolean confirmed;
    private boolean released;
    private final Instant createdAt;

    private StockReservation(
            final StockReservationId id,
            final ProductId productId,
            final UUID orderId,
            final int quantity,
            final Instant expiresAt,
            final boolean confirmed,
            final boolean released,
            final Instant createdAt
    ) {
        super(id, 0);
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.confirmed = confirmed;
        this.released = released;
        this.createdAt = createdAt;
    }

    /**
     * Factory: creates a new stock reservation.
     */
    public static StockReservation create(
            final ProductId productId,
            final UUID orderId,
            final int quantity,
            final Instant expiresAt
    ) {
        final var reservation = new StockReservation(
                StockReservationId.unique(),
                productId, orderId, quantity, expiresAt,
                false, false, Instant.now()
        );

        final var notification = Notification.create();
        reservation.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        return reservation;
    }

    /**
     * Factory: hydrates a StockReservation from persistence.
     */
    public static StockReservation with(
            final StockReservationId id,
            final ProductId productId,
            final UUID orderId,
            final int quantity,
            final Instant expiresAt,
            final boolean confirmed,
            final boolean released,
            final Instant createdAt
    ) {
        return new StockReservation(id, productId, orderId, quantity, expiresAt, confirmed, released, createdAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void confirm() {
        if (this.confirmed) {
            throw DomainException.with(StockReservationError.RESERVATION_ALREADY_CONFIRMED);
        }
        if (this.released) {
            throw DomainException.with(StockReservationError.RESERVATION_ALREADY_RELEASED);
        }
        this.confirmed = true;
        registerEvent(new StockConfirmedEvent(
                getId().getValue().toString(),
                productId.getValue().toString(),
                quantity,
                orderId
        ));
    }

    public void release() {
        if (this.released) {
            throw DomainException.with(StockReservationError.RESERVATION_ALREADY_RELEASED);
        }
        if (this.confirmed) {
            throw DomainException.with(StockReservationError.RESERVATION_ALREADY_CONFIRMED);
        }
        this.released = true;
        registerEvent(new StockReleasedEvent(
                getId().getValue().toString(),
                productId.getValue().toString(),
                quantity
        ));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isActive() {
        return !confirmed && !released && !isExpired();
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new StockReservationValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public ProductId getProductId() { return productId; }
    public UUID getOrderId() { return orderId; }
    public int getQuantity() { return quantity; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConfirmed() { return confirmed; }
    public boolean isReleased() { return released; }
    public Instant getCreatedAt() { return createdAt; }
}

