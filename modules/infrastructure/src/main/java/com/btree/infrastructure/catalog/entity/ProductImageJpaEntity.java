package com.btree.infrastructure.catalog.entity;


import com.btree.domain.catalog.entity.ProductImage;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductImageId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.product_images}.
 * Child of {@link ProductJpaEntity} — deleted via ON DELETE CASCADE.
 */
@Entity
@Table(name = "product_images", schema = "catalog")
public class ProductImageJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "url", nullable = false, length = 512)
    private String url;

    @Column(name = "alt_text", length = 256)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProductImageJpaEntity() {}

    public static ProductImageJpaEntity from(final ProductImage image, final ProductJpaEntity productEntity) {
        final var entity = new ProductImageJpaEntity();
        entity.id = image.getId().getValue();
        entity.product = productEntity;
        entity.url = image.getUrl();
        entity.altText = image.getAltText();
        entity.sortOrder = image.getSortOrder();
        entity.primary = image.isPrimary();
        entity.createdAt = image.getCreatedAt();
        return entity;
    }

    public ProductImage toAggregate() {
        return ProductImage.with(
                ProductImageId.from(this.id),
                ProductId.from(this.product.getId()),
                this.url,
                this.altText,
                this.sortOrder,
                this.primary,
                this.createdAt
        );
    }

    public void updateFrom(final ProductImage image) {
        this.url = image.getUrl();
        this.altText = image.getAltText();
        this.sortOrder = image.getSortOrder();
        this.primary = image.isPrimary();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductJpaEntity getProduct() { return product; }
    public void setProduct(ProductJpaEntity product) { this.product = product; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
