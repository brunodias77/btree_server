package com.btree.domain.cart.entity;

import com.btree.domain.cart.identifier.CartId;
import com.btree.domain.cart.identifier.CartItemId;
import com.btree.domain.cart.validator.CartItemValidator;
import com.btree.shared.domain.DomainException;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity — maps to {@code cart.cart_items}.
 *
 * <p>Child of {@link Cart} aggregate (ON DELETE CASCADE).
 * UNIQUE constraint: (cart_id, product_id).
 */
public class CartItem extends Entity<CartItemId> {

    private final CartId cartId;
    private final UUID productId;
    private int quantity;
    private BigDecimal unitPrice;
    private Instant createdAt;
    private Instant updatedAt;

    private CartItem(
            final CartItemId id,
            final CartId cartId,
            final UUID productId,
            final int quantity,
            final BigDecimal unitPrice,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        super(id);
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CartItem create(
            final CartId cartId,
            final UUID productId,
            final int quantity,
            final BigDecimal unitPrice
    ) {
        final var now = Instant.now();
        final var item = new CartItem(
                CartItemId.unique(), cartId, productId, quantity, unitPrice, now, now
        );
        final var notification = Notification.create();
        item.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }
        return item;
    }

    public static CartItem with(
            final CartItemId id,
            final CartId cartId,
            final UUID productId,
            final int quantity,
            final BigDecimal unitPrice,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        return new CartItem(id, cartId, productId, quantity, unitPrice, createdAt, updatedAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void updateQuantity(final int quantity) {
        if (quantity <= 0) throw DomainException.with(
                new com.btree.shared.validation.Error("'quantity' deve ser maior que zero")
        );
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public void updatePrice(final BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.updatedAt = Instant.now();
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new CartItemValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public CartId getCartId()       { return cartId; }
    public UUID getProductId()      { return productId; }
    public int getQuantity()        { return quantity; }
    public BigDecimal getUnitPrice(){ return unitPrice; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
