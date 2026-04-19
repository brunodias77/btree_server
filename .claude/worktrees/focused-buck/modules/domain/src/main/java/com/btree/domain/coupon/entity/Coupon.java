package com.btree.domain.coupon.entity;

import com.btree.domain.coupon.event.CouponUpdatedEvent;
import com.btree.domain.coupon.identifier.CouponId;
import com.btree.domain.coupon.validator.CouponValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — maps to {@code coupons.coupons} table.
 *
 * <p>Owns: eligible_categories, eligible_products, eligible_brands, eligible_users,
 * coupon_usages, coupon_reservations.
 */
public class Coupon extends AggregateRoot<CouponId> {

    private String code;
    private String description;
    private CouponType couponType;
    private CouponScope couponScope;
    private CouponStatus status;
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscountAmount;
    private Integer maxUses;
    private int maxUsesPerUser;
    private int currentUses;
    private Instant startsAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private List<UUID> eligibleCategoryIds;
    private List<UUID> eligibleProductIds;
    private List<UUID> eligibleBrandIds;
    private List<UUID> eligibleUserIds;

    private Coupon(
            final CouponId id,
            final String code,
            final String description,
            final CouponType couponType,
            final CouponScope couponScope,
            final CouponStatus status,
            final BigDecimal discountValue,
            final BigDecimal minOrderValue,
            final BigDecimal maxDiscountAmount,
            final Integer maxUses,
            final int maxUsesPerUser,
            final int currentUses,
            final Instant startsAt,
            final Instant expiresAt,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt,
            final List<UUID> eligibleCategoryIds,
            final List<UUID> eligibleProductIds,
            final List<UUID> eligibleBrandIds,
            final List<UUID> eligibleUserIds,
            final int version
    ) {
        super(id, version);
        this.code                = code;
        this.description         = description;
        this.couponType          = couponType;
        this.couponScope         = couponScope;
        this.status              = status;
        this.discountValue       = discountValue;
        this.minOrderValue       = minOrderValue;
        this.maxDiscountAmount   = maxDiscountAmount;
        this.maxUses             = maxUses;
        this.maxUsesPerUser      = maxUsesPerUser;
        this.currentUses         = currentUses;
        this.startsAt            = startsAt;
        this.expiresAt           = expiresAt;
        this.createdAt           = createdAt;
        this.updatedAt           = updatedAt;
        this.deletedAt           = deletedAt;
        this.eligibleCategoryIds = eligibleCategoryIds != null ? new ArrayList<>(eligibleCategoryIds) : new ArrayList<>();
        this.eligibleProductIds  = eligibleProductIds  != null ? new ArrayList<>(eligibleProductIds)  : new ArrayList<>();
        this.eligibleBrandIds    = eligibleBrandIds    != null ? new ArrayList<>(eligibleBrandIds)    : new ArrayList<>();
        this.eligibleUserIds     = eligibleUserIds     != null ? new ArrayList<>(eligibleUserIds)     : new ArrayList<>();
    }

    /**
     * Factory: reconstrói o aggregate a partir do banco (hidratação).
     */
    public static Coupon with(
            final CouponId id,
            final String code,
            final String description,
            final CouponType couponType,
            final CouponScope couponScope,
            final CouponStatus status,
            final BigDecimal discountValue,
            final BigDecimal minOrderValue,
            final BigDecimal maxDiscountAmount,
            final Integer maxUses,
            final int maxUsesPerUser,
            final int currentUses,
            final Instant startsAt,
            final Instant expiresAt,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt,
            final List<UUID> eligibleCategoryIds,
            final List<UUID> eligibleProductIds,
            final List<UUID> eligibleBrandIds,
            final List<UUID> eligibleUserIds,
            final int version
    ) {
        return new Coupon(
                id, code, description, couponType, couponScope, status, discountValue,
                minOrderValue, maxDiscountAmount, maxUses, maxUsesPerUser, currentUses,
                startsAt, expiresAt, createdAt, updatedAt, deletedAt,
                eligibleCategoryIds, eligibleProductIds, eligibleBrandIds, eligibleUserIds, version
        );
    }

    // ── Domain Behaviors ─────────────────────────────────────

    /**
     * Aplica os novos valores mutáveis ao aggregate.
     *
     * <p>Executa validação estrutural via {@link CouponValidator}. Se não houver
     * erros, registra {@link CouponUpdatedEvent} no outbox.
     * Campos imutáveis ({@code code}, {@code couponType}, {@code couponScope},
     * {@code status}, {@code currentUses}, {@code createdAt}) nunca são alterados aqui.
     */
    public void update(
            final String description,
            final BigDecimal discountValue,
            final BigDecimal minOrderValue,
            final BigDecimal maxDiscountAmount,
            final Integer maxUses,
            final int maxUsesPerUser,
            final Instant startsAt,
            final Instant expiresAt,
            final List<UUID> eligibleCategoryIds,
            final List<UUID> eligibleProductIds,
            final List<UUID> eligibleBrandIds,
            final List<UUID> eligibleUserIds,
            final Notification notification
    ) {
        this.description         = description;
        this.discountValue       = discountValue;
        this.minOrderValue       = minOrderValue;
        this.maxDiscountAmount   = maxDiscountAmount;
        this.maxUses             = maxUses;
        this.maxUsesPerUser      = maxUsesPerUser;
        this.startsAt            = startsAt;
        this.expiresAt           = expiresAt;
        this.eligibleCategoryIds = eligibleCategoryIds != null ? new ArrayList<>(eligibleCategoryIds) : new ArrayList<>();
        this.eligibleProductIds  = eligibleProductIds  != null ? new ArrayList<>(eligibleProductIds)  : new ArrayList<>();
        this.eligibleBrandIds    = eligibleBrandIds    != null ? new ArrayList<>(eligibleBrandIds)    : new ArrayList<>();
        this.eligibleUserIds     = eligibleUserIds     != null ? new ArrayList<>(eligibleUserIds)     : new ArrayList<>();
        this.updatedAt           = Instant.now();

        this.validate(notification);

        if (!notification.hasError()) {
            registerEvent(new CouponUpdatedEvent(
                    this.id.getValue().toString(),
                    this.code,
                    this.couponType,
                    this.discountValue
            ));
        }
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new CouponValidator(this, handler).validate();
    }

    // ── Queries ──────────────────────────────────────────────

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // ── Getters ──────────────────────────────────────────────

    public String getCode()                       { return code; }
    public String getDescription()                { return description; }
    public CouponType getCouponType()             { return couponType; }
    public CouponScope getCouponScope()           { return couponScope; }
    public CouponStatus getStatus()               { return status; }
    public BigDecimal getDiscountValue()          { return discountValue; }
    public BigDecimal getMinOrderValue()          { return minOrderValue; }
    public BigDecimal getMaxDiscountAmount()      { return maxDiscountAmount; }
    public Integer getMaxUses()                   { return maxUses; }
    public int getMaxUsesPerUser()                { return maxUsesPerUser; }
    public int getCurrentUses()                   { return currentUses; }
    public Instant getStartsAt()                  { return startsAt; }
    public Instant getExpiresAt()                 { return expiresAt; }
    public Instant getCreatedAt()                 { return createdAt; }
    public Instant getUpdatedAt()                 { return updatedAt; }
    public Instant getDeletedAt()                 { return deletedAt; }
    public List<UUID> getEligibleCategoryIds()    { return Collections.unmodifiableList(eligibleCategoryIds); }
    public List<UUID> getEligibleProductIds()     { return Collections.unmodifiableList(eligibleProductIds); }
    public List<UUID> getEligibleBrandIds()       { return Collections.unmodifiableList(eligibleBrandIds); }
    public List<UUID> getEligibleUserIds()        { return Collections.unmodifiableList(eligibleUserIds); }
}
