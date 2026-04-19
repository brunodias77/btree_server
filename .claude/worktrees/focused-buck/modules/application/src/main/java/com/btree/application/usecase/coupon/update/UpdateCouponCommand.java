package com.btree.application.usecase.coupon.update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponCommand(
        UUID id,
        String description,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Integer maxUses,
        int maxUsesPerUser,
        Instant startsAt,
        Instant expiresAt,
        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds
) {}
