package com.btree.api.dto.request.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponRequest(

        String description,

        @NotNull(message = "discountValue é obrigatório")
        @DecimalMin(value = "0.01", message = "discountValue deve ser maior que zero")
        BigDecimal discountValue,

        @DecimalMin(value = "0.00", inclusive = true, message = "minOrderValue deve ser maior ou igual a zero")
        BigDecimal minOrderValue,

        @DecimalMin(value = "0.01", message = "maxDiscountAmount deve ser maior que zero")
        BigDecimal maxDiscountAmount,

        @Min(value = 1, message = "maxUses deve ser maior ou igual a 1")
        Integer maxUses,

        @NotNull(message = "maxUsesPerUser é obrigatório")
        @Min(value = 1, message = "maxUsesPerUser deve ser maior ou igual a 1")
        Integer maxUsesPerUser,

        @NotNull(message = "startsAt é obrigatório")
        Instant startsAt,

        Instant expiresAt,

        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds
) {}
