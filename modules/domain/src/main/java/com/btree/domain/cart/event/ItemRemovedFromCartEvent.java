package com.btree.domain.cart.event;
import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento disparado quando um produto é removido do carrinho.
 */
public class ItemRemovedFromCartEvent extends DomainEvent {

    private final String cartId;
    private final UUID productId;

    public ItemRemovedFromCartEvent(final String cartId, final UUID productId) {
        super();
        this.cartId = cartId;
        this.productId = productId;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.item_removed"; }

    public String getCartId()  { return cartId; }
    public UUID getProductId() { return productId; }
}
