package com.btree.domain.cart.event;
import com.btree.shared.domain.DomainEvent;

/**
 * Evento disparado quando um cupom é aplicado ao carrinho.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Criação de reserva de cupom (CouponReservation)</li>
 * </ul>
 */
public class CartCouponAppliedEvent extends DomainEvent {

    private final String cartId;
    private final String couponCode;

    public CartCouponAppliedEvent(final String cartId, final String couponCode) {
        super();
        this.cartId = cartId;
        this.couponCode = couponCode;
    }

    @Override public String getAggregateId()   { return cartId; }
    @Override public String getAggregateType() { return "Cart"; }
    @Override public String getEventType()     { return "cart.coupon_applied"; }

    public String getCartId()    { return cartId; }
    public String getCouponCode() { return couponCode; }
}
