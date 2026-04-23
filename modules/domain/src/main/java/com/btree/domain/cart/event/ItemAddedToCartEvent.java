package com.btree.domain.cart.event;
import com.btree.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento disparado quando um produto é adicionado ao carrinho.
 */
public class ItemAddedToCartEvent extends DomainEvent {

    private final String cartId;
    private final UUID productId;
    private final int quantity;
    private final BigDecimal unitPrice;

    public ItemAddedToCartEvent(
            final String cartId,
            final UUID productId,
            final int quantity,
            final BigDecimal unitPrice
    ) {
        super();
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.item_added"; }

    public String getCartId()        { return cartId; }
    public UUID getProductId()       { return productId; }
    public int getQuantity()         { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
