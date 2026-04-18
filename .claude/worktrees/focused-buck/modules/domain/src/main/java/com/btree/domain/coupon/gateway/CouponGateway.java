package com.btree.domain.coupon.gateway;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.identifier.CouponId;

import java.util.Optional;

public interface CouponGateway {
    Coupon save(Coupon coupon);
    Coupon update(Coupon coupon);
    Optional<Coupon> findById(CouponId id);
    boolean existsByCode(String code);
}
