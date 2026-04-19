package com.btree.api.dto.response.coupon;

import com.btree.application.usecase.coupon.update.UpdateCouponOutput;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponResponse(
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
    public static UpdateCouponResponse from(final UpdateCouponOutput output) {
        return new UpdateCouponResponse(
                output.id(),
                output.code(),
                output.description(),
                output.couponType(),
                output.couponScope(),
                output.status(),
                output.discountValue(),
                output.minOrderValue(),
                output.maxDiscountAmount(),
                output.maxUses(),
                output.maxUsesPerUser(),
                output.currentUses(),
                output.startsAt(),
                output.expiresAt(),
                output.eligibleCategoryIds(),
                output.eligibleProductIds(),
                output.eligibleBrandIds(),
                output.eligibleUserIds(),
                output.updatedAt(),
                output.version()
        );
    }
}
