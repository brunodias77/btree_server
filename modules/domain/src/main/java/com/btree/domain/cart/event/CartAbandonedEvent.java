package com.btree.domain.cart.event;

import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento disparado quando um carrinho é marcado como abandonado.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Envio de e-mail de recuperação de carrinho</li>
 *   <li>Liberação de reservas de cupom pendentes</li>
 * </ul>
 */
public class CartAbandonedEvent extends DomainEvent {

    private final String cartId;
    private final UUID userId;

    public CartAbandonedEvent(final String cartId, final UUID userId) {
        super();
        this.cartId = cartId;
        this.userId = userId;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.abandoned"; }

    public String getCartId() { return cartId; }
    public UUID getUserId()   { return userId; }
}
