package com.btree.domain.coupon.event;

import com.btree.shared.domain.DomainEvent;
import com.btree.shared.enums.CouponType;

import java.math.BigDecimal;

/**
 * Evento de domínio disparado quando um cupom é atualizado com sucesso.
 *
 * <p>Persistido no outbox ({@code shared.domain_events}) e processado
 * assincronamente pelo job {@code ProcessDomainEventsUseCase}.
 */
public class CouponUpdatedEvent extends DomainEvent {

    private final String couponId;
    private final String couponCode;
    private final CouponType couponType;
    private final BigDecimal discountValue;

    public CouponUpdatedEvent(
            final String couponId,
            final String couponCode,
            final CouponType couponType,
            final BigDecimal discountValue
    ) {
        super();
        this.couponId      = couponId;
        this.couponCode    = couponCode;
        this.couponType    = couponType;
        this.discountValue = discountValue;
    }

    @Override
    public String getAggregateId() { return couponId; }

    @Override
    public String getAggregateType() { return "Coupon"; }

    @Override
    public String getEventType() { return "coupon.updated"; }

    public String getCouponCode()        { return couponCode; }
    public CouponType getCouponType()    { return couponType; }
    public BigDecimal getDiscountValue() { return discountValue; }
}
