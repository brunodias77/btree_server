package com.btree.domain.cart.event;

import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento disparado quando um novo carrinho é criado (autenticado ou guest).
 */
public class CartCreatedEvent extends DomainEvent {

    private final String cartId;
    private final UUID userId;
    private final String sessionId;

    public CartCreatedEvent(final String cartId, final UUID userId, final String sessionId) {
        super();
        this.cartId = cartId;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.created"; }

    public String getCartId()    { return cartId; }
    public UUID getUserId()      { return userId; }
    public String getSessionId() { return sessionId; }
}
