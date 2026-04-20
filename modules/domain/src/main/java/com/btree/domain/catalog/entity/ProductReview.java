package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductReviewId;
import com.btree.domain.catalog.validator.ProductReviewValidator;
import com.btree.shared.domain.DomainException;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.UUID;

import com.btree.domain.catalog.error.ProductReviewError;

/**
 * Entity — maps to {@code catalog.product_reviews} table.
 * Soft delete via {@code deleted_at}.
 */
public class ProductReview extends Entity<ProductReviewId> {

    private final ProductId productId;
    private final UUID userId;
    private int rating;
    private String title;
    private String comment;
    private final boolean verifiedPurchase;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private ProductReview(
            final ProductReviewId id,
            final ProductId productId,
            final UUID userId,
            final int rating,
            final String title,
            final String comment,
            final boolean verifiedPurchase,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        super(id);
        this.productId = productId;
        this.userId = userId;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.verifiedPurchase = verifiedPurchase;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static ProductReview create(
            final ProductId productId,
            final UUID userId,
            final int rating,
            final String title,
            final String comment,
            final boolean verifiedPurchase
    ) {
        final var now = Instant.now();
        final var review = new ProductReview(
                ProductReviewId.unique(),
                productId, userId, rating, title, comment,
                verifiedPurchase, now, now, null
        );

        final var notification = Notification.create();
        review.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        return review;
    }

    public static ProductReview with(
            final ProductReviewId id,
            final ProductId productId,
            final UUID userId,
            final int rating,
            final String title,
            final String comment,
            final boolean verifiedPurchase,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        return new ProductReview(id, productId, userId, rating, title, comment, verifiedPurchase, createdAt, updatedAt, deletedAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void update(final int rating, final String title, final String comment) {
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        if (this.deletedAt != null) {
            throw DomainException.with(ProductReviewError.REVIEW_ALREADY_DELETED);
        }
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new ProductReviewValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public ProductId getProductId() { return productId; }
    public UUID getUserId() { return userId; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getComment() { return comment; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
