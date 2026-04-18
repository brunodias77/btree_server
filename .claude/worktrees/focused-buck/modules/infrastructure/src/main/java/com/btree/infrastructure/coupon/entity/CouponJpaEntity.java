package com.btree.infrastructure.coupon.entity;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.identifier.CouponId;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity para a tabela {@code coupons.coupons}.
 *
 * <p>As coleções de elegibilidade ({@code eligible_categories}, {@code eligible_products},
 * {@code eligible_brands}, {@code eligible_users}) são mapeadas como
 * {@link ElementCollection} — ao chamar {@link #updateFrom(Coupon)}, a coleção é
 * limpa e repovoada, e o Hibernate executa os DELETEs + INSERTs automaticamente.
 *
 * <p>O {@code schema} deve ser declarado explicitamente em todas as anotações
 * {@link Table} e {@link CollectionTable} para garantir compatibilidade com
 * Testcontainers e pipelines CI onde o {@code search_path} é apenas "public".
 */
@Entity
@Table(name = "coupons", schema = "coupons")
public class CouponJpaEntity {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType couponType;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_scope", nullable = false)
    private CouponScope couponScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "max_uses_per_user", nullable = false)
    private int maxUsesPerUser;

    @Column(name = "current_uses", nullable = false)
    private int currentUses;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "coupons",
            name = "eligible_categories",
            joinColumns = @JoinColumn(name = "coupon_id")
    )
    @Column(name = "category_id")
    private List<UUID> eligibleCategoryIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "coupons",
            name = "eligible_products",
            joinColumns = @JoinColumn(name = "coupon_id")
    )
    @Column(name = "product_id")
    private List<UUID> eligibleProductIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "coupons",
            name = "eligible_brands",
            joinColumns = @JoinColumn(name = "coupon_id")
    )
    @Column(name = "brand_id")
    private List<UUID> eligibleBrandIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "coupons",
            name = "eligible_users",
            joinColumns = @JoinColumn(name = "coupon_id")
    )
    @Column(name = "user_id")
    private List<UUID> eligibleUserIds = new ArrayList<>();

    public CouponJpaEntity() {}

    private CouponJpaEntity(
            final UUID id,
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
            final Instant deletedAt
    ) {
        this.id                = id;
        this.code              = code;
        this.description       = description;
        this.couponType        = couponType;
        this.couponScope       = couponScope;
        this.status            = status;
        this.discountValue     = discountValue;
        this.minOrderValue     = minOrderValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.maxUses           = maxUses;
        this.maxUsesPerUser    = maxUsesPerUser;
        this.currentUses       = currentUses;
        this.startsAt          = startsAt;
        this.expiresAt         = expiresAt;
        this.createdAt         = createdAt;
        this.updatedAt         = updatedAt;
        this.deletedAt         = deletedAt;
    }

    public static CouponJpaEntity from(final Coupon aggregate) {
        final var entity = new CouponJpaEntity(
                aggregate.getId().getValue(),
                aggregate.getCode(),
                aggregate.getDescription(),
                aggregate.getCouponType(),
                aggregate.getCouponScope(),
                aggregate.getStatus(),
                aggregate.getDiscountValue(),
                aggregate.getMinOrderValue(),
                aggregate.getMaxDiscountAmount(),
                aggregate.getMaxUses(),
                aggregate.getMaxUsesPerUser(),
                aggregate.getCurrentUses(),
                aggregate.getStartsAt(),
                aggregate.getExpiresAt(),
                aggregate.getCreatedAt(),
                aggregate.getUpdatedAt(),
                aggregate.getDeletedAt()
        );
        entity.eligibleCategoryIds.addAll(aggregate.getEligibleCategoryIds());
        entity.eligibleProductIds.addAll(aggregate.getEligibleProductIds());
        entity.eligibleBrandIds.addAll(aggregate.getEligibleBrandIds());
        entity.eligibleUserIds.addAll(aggregate.getEligibleUserIds());
        return entity;
    }

    public Coupon toAggregate() {
        return Coupon.with(
                CouponId.from(this.id),
                this.code,
                this.description,
                this.couponType,
                this.couponScope,
                this.status,
                this.discountValue,
                this.minOrderValue,
                this.maxDiscountAmount,
                this.maxUses,
                this.maxUsesPerUser,
                this.currentUses,
                this.startsAt,
                this.expiresAt,
                this.createdAt,
                this.updatedAt,
                this.deletedAt,
                new ArrayList<>(this.eligibleCategoryIds),
                new ArrayList<>(this.eligibleProductIds),
                new ArrayList<>(this.eligibleBrandIds),
                new ArrayList<>(this.eligibleUserIds),
                this.version
        );
    }

    /**
     * Atualiza campos mutáveis preservando {@code id}, {@code code}, {@code coupon_type},
     * {@code coupon_scope}, {@code status}, {@code current_uses}, {@code created_at},
     * {@code deleted_at} e {@code version} (gerenciado pelo {@code @Version} do Hibernate).
     */
    public void updateFrom(final Coupon aggregate) {
        this.description       = aggregate.getDescription();
        this.discountValue     = aggregate.getDiscountValue();
        this.minOrderValue     = aggregate.getMinOrderValue();
        this.maxDiscountAmount = aggregate.getMaxDiscountAmount();
        this.maxUses           = aggregate.getMaxUses();
        this.maxUsesPerUser    = aggregate.getMaxUsesPerUser();
        this.startsAt          = aggregate.getStartsAt();
        this.expiresAt         = aggregate.getExpiresAt();
        this.updatedAt         = aggregate.getUpdatedAt();

        // Coleções: limpar e repovoar — Hibernate executa DELETE + INSERT automaticamente
        this.eligibleCategoryIds.clear();
        this.eligibleCategoryIds.addAll(aggregate.getEligibleCategoryIds());

        this.eligibleProductIds.clear();
        this.eligibleProductIds.addAll(aggregate.getEligibleProductIds());

        this.eligibleBrandIds.clear();
        this.eligibleBrandIds.addAll(aggregate.getEligibleBrandIds());

        this.eligibleUserIds.clear();
        this.eligibleUserIds.addAll(aggregate.getEligibleUserIds());
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId()                         { return id; }
    public String getCode()                     { return code; }
    public String getDescription()              { return description; }
    public CouponType getCouponType()           { return couponType; }
    public CouponScope getCouponScope()         { return couponScope; }
    public CouponStatus getStatus()             { return status; }
    public BigDecimal getDiscountValue()        { return discountValue; }
    public BigDecimal getMinOrderValue()        { return minOrderValue; }
    public BigDecimal getMaxDiscountAmount()    { return maxDiscountAmount; }
    public Integer getMaxUses()                 { return maxUses; }
    public int getMaxUsesPerUser()              { return maxUsesPerUser; }
    public int getCurrentUses()                 { return currentUses; }
    public Instant getStartsAt()                { return startsAt; }
    public Instant getExpiresAt()               { return expiresAt; }
    public Instant getCreatedAt()               { return createdAt; }
    public Instant getUpdatedAt()               { return updatedAt; }
    public Instant getDeletedAt()               { return deletedAt; }
    public int getVersion()                     { return version; }
    public List<UUID> getEligibleCategoryIds()  { return eligibleCategoryIds; }
    public List<UUID> getEligibleProductIds()   { return eligibleProductIds; }
    public List<UUID> getEligibleBrandIds()     { return eligibleBrandIds; }
    public List<UUID> getEligibleUserIds()      { return eligibleUserIds; }
}
