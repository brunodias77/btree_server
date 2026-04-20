package com.btree.infrastructure.catalog.entity;

import com.btree.domain.catalog.entity.ProductReview;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductReviewId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.product_reviews}.
 * Soft delete via {@code deleted_at}.
 */
@Entity
@Table(name = "product_reviews", schema = "catalog")
public class ProductReviewJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ProductReviewJpaEntity() {}

    public static ProductReviewJpaEntity from(final ProductReview review) {
        final var entity = new ProductReviewJpaEntity();
        entity.id = review.getId().getValue();
        entity.productId = review.getProductId().getValue();
        entity.userId = review.getUserId();
        entity.rating = review.getRating();
        entity.title = review.getTitle();
        entity.comment = review.getComment();
        entity.verifiedPurchase = review.isVerifiedPurchase();
        entity.createdAt = review.getCreatedAt();
        entity.updatedAt = review.getUpdatedAt();
        entity.deletedAt = review.getDeletedAt();
        return entity;
    }

    public ProductReview toAggregate() {
        return ProductReview.with(
                ProductReviewId.from(this.id),
                ProductId.from(this.productId),
                this.userId,
                this.rating,
                this.title,
                this.comment,
                this.verifiedPurchase,
                this.createdAt,
                this.updatedAt,
                this.deletedAt
        );
    }

    public void updateFrom(final ProductReview review) {
        this.rating = review.getRating();
        this.title = review.getTitle();
        this.comment = review.getComment();
        this.updatedAt = review.getUpdatedAt();
        this.deletedAt = review.getDeletedAt();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public void setVerifiedPurchase(boolean verifiedPurchase) { this.verifiedPurchase = verifiedPurchase; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
