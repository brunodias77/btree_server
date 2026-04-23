package com.btree.domain.cart.event;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento disparado quando um cupom é removido do carrinho.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Liberação da reserva de cupom (CouponReservation)</li>
 * </ul>
 */
public class CartCouponRemovedEvent extends DomainEvent {

    private final String cartId;
    private final String couponCode;

    public CartCouponRemovedEvent(final String cartId, final String couponCode) {
        super();
        this.cartId = cartId;
        this.couponCode = couponCode;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.coupon_removed"; }

    public String getCartId()    { return cartId; }
    public String getCouponCode() { return couponCode; }
}
