package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductImageId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

/**
 * Entity — maps to {@code catalog.product_images} table.
 * Child of Product aggregate (ON DELETE CASCADE).
 */
public class ProductImage extends Entity<ProductImageId> {

    private final ProductId productId;
    private String url;
    private String altText;
    private int sortOrder;
    private boolean primary;
    private final Instant createdAt;

    private ProductImage(
            final ProductImageId id,
            final ProductId productId,
            final String url,
            final String altText,
            final int sortOrder,
            final boolean primary,
            final Instant createdAt
    ) {
        super(id);
        this.productId = productId;
        this.url = url;
        this.altText = altText;
        this.sortOrder = sortOrder;
        this.primary = primary;
        this.createdAt = createdAt;
    }

    public static ProductImage create(
            final ProductId productId,
            final String url,
            final String altText,
            final int sortOrder,
            final boolean primary
    ) {
        return new ProductImage(
                ProductImageId.unique(),
                productId, url, altText, sortOrder, primary,
                Instant.now()
        );
    }

    public static ProductImage with(
            final ProductImageId id,
            final ProductId productId,
            final String url,
            final String altText,
            final int sortOrder,
            final boolean primary,
            final Instant createdAt
    ) {
        return new ProductImage(id, productId, url, altText, sortOrder, primary, createdAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void update(final String url, final String altText, final int sortOrder) {
        this.url = url;
        this.altText = altText;
        this.sortOrder = sortOrder;
    }

    public void markAsPrimary() {
        this.primary = true;
    }

    public void unmarkAsPrimary() {
        this.primary = false;
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        // Validation handled at Product aggregate level
    }

    // ── Getters ──────────────────────────────────────────────

    public ProductId getProductId() { return productId; }
    public String getUrl() { return url; }
    public String getAltText() { return altText; }
    public int getSortOrder() { return sortOrder; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
}
