package com.btree.domain.cart.event;

import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento disparado quando um carrinho é convertido em pedido (checkout concluído).
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Criação do pedido (PlaceOrderUseCase)</li>
 *   <li>Confirmação de uso do cupom</li>
 * </ul>
 */
public class CartConvertedEvent extends DomainEvent {

    private final String cartId;
    private final UUID userId;

    public CartConvertedEvent(final String cartId, final UUID userId) {
        super();
        this.cartId = cartId;
        this.userId = userId;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.converted"; }

    public String getCartId() { return cartId; }
    public UUID getUserId()   { return userId; }
}
