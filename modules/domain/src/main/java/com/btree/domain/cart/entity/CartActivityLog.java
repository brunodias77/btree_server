package com.btree.domain.cart.entity;

import com.btree.domain.cart.identifier.CartActivityLogId;
import com.btree.domain.cart.identifier.CartId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity — maps to {@code cart.cart_activity_log}.
 *
 * <p>Immutable audit log of cart actions (add, remove, coupon apply, etc.).
 * Child of Cart (ON DELETE CASCADE).
 */
public class CartActivityLog extends Entity<CartActivityLogId> {

    private final CartId cartId;
    private final String action;
    private final UUID productId;
    private final Integer quantity;
    private final String details;
    private final Instant createdAt;

    private CartActivityLog(
            final CartActivityLogId id,
            final CartId cartId,
            final String action,
            final UUID productId,
            final Integer quantity,
            final String details,
            final Instant createdAt
    ) {
        super(id);
        this.cartId = cartId;
        this.action = action;
        this.productId = productId;
        this.quantity = quantity;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static CartActivityLog create(
            final CartId cartId,
            final String action,
            final UUID productId,
            final Integer quantity,
            final String details
    ) {
        return new CartActivityLog(
                CartActivityLogId.unique(), cartId,
                action, productId, quantity, details,
                Instant.now()
        );
    }

    public static CartActivityLog with(
            final CartActivityLogId id,
            final CartId cartId,
            final String action,
            final UUID productId,
            final Integer quantity,
            final String details,
            final Instant createdAt
    ) {
        return new CartActivityLog(id, cartId, action, productId, quantity, details, createdAt);
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        // Immutable — validation done at use case level
    }

    // ── Getters ──────────────────────────────────────────────

    public CartId getCartId()      { return cartId; }
    public String getAction()      { return action; }
    public UUID getProductId()     { return productId; }
    public Integer getQuantity()   { return quantity; }
    public String getDetails()     { return details; }
    public Instant getCreatedAt()  { return createdAt; }
}

