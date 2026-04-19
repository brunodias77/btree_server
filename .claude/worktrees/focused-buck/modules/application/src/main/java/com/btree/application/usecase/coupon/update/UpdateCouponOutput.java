package com.btree.application.usecase.coupon.update;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponOutput(
        UUID id,
        String code,
        String description,
        CouponType couponType,
        CouponScope couponScope,
        CouponStatus status,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Integer maxUses,
        int maxUsesPerUser,
        int currentUses,
        Instant startsAt,
        Instant expiresAt,
        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds,
        Instant updatedAt,
        int version
) {
    public static UpdateCouponOutput from(final Coupon coupon) {
        return new UpdateCouponOutput(
                coupon.getId().getValue(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getCouponType(),
                coupon.getCouponScope(),
                coupon.getStatus(),
                coupon.getDiscountValue(),
                coupon.getMinOrderValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMaxUses(),
                coupon.getMaxUsesPerUser(),
                coupon.getCurrentUses(),
                coupon.getStartsAt(),
                coupon.getExpiresAt(),
                coupon.getEligibleCategoryIds(),
                coupon.getEligibleProductIds(),
                coupon.getEligibleBrandIds(),
                coupon.getEligibleUserIds(),
                coupon.getUpdatedAt(),
                coupon.getVersion()
        );
    }
}
