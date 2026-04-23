package com.btree.domain.cart.event;
import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento disparado quando um carrinho guest (sem autenticação) expira por inatividade.
 *
 * <p>Processado pelo job {@code ExpireAbandonedCarts}.
 */
public class CartExpiredEvent extends DomainEvent {

    private final String cartId;
    private final UUID userId;

    public CartExpiredEvent(final String cartId, final UUID userId) {
        super();
        this.cartId = cartId;
        this.userId = userId;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.expired"; }

    public String getCartId() { return cartId; }
    public UUID getUserId()   { return userId; }
}
