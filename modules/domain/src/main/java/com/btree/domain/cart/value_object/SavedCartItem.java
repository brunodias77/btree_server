package com.btree.domain.cart.value_object;

import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representando um item em um carrinho salvo ({@code SavedCart}).
 *
 * <p>Maps to an element inside {@code cart.saved_carts.items} JSONB array.
 * Immutable: productId + quantity snapshot.
 */
public class SavedCartItem extends ValueObject {

    private final UUID productId;
    private final int quantity;

    private SavedCartItem(final UUID productId, final int quantity) {
        Objects.requireNonNull(productId, "'productId' não pode ser nulo");
        if (quantity <= 0) {
            throw new IllegalArgumentException("'quantity' deve ser maior que zero");
        }
        this.productId = productId;
        this.quantity = quantity;
    }

    public static SavedCartItem of(final UUID productId, final int quantity) {
        return new SavedCartItem(productId, quantity);
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SavedCartItem that = (SavedCartItem) o;
        return quantity == that.quantity && productId.equals(that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity);
    }
}
