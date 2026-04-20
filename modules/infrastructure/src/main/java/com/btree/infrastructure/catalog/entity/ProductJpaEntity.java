package com.btree.infrastructure.catalog.entity;


import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;

import com.btree.domain.catalog.value_object.ProductDimensions;
import com.btree.shared.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.products}.
 * Optimistic locking via {@code @Version}. Soft delete via {@code deleted_at}.
 * Owns {@link ProductImageJpaEntity} via cascade.
 */
@Entity
@Table(name = "products", schema = "catalog")
public class ProductJpaEntity {

    @Id
    private UUID id;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "slug", nullable = false, length = 350, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "sku", nullable = false, length = 50, unique = true)
    private String sku;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Column(name = "weight", precision = 8, scale = 3)
    private BigDecimal weight;

    @Column(name = "width", precision = 8, scale = 2)
    private BigDecimal width;

    @Column(name = "height", precision = 8, scale = 2)
    private BigDecimal height;

    @Column(name = "depth", precision = 8, scale = 2)
    private BigDecimal depth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "shared.product_status")
    @ColumnTransformer(write = "?::shared.product_status")
    private ProductStatus status;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductImageJpaEntity> images = new ArrayList<>();

    public ProductJpaEntity() {}

    public static ProductJpaEntity from(final Product product) {
        final var entity = new ProductJpaEntity();
        entity.id = product.getId().getValue();
        entity.categoryId = product.getCategoryId() != null ? product.getCategoryId().getValue() : null;
        entity.brandId = product.getBrandId() != null ? product.getBrandId().getValue() : null;
        entity.name = product.getName();
        entity.slug = product.getSlug();
        entity.description = product.getDescription();
        entity.shortDescription = product.getShortDescription();
        entity.sku = product.getSku();
        entity.price = product.getPrice();
        entity.compareAtPrice = product.getCompareAtPrice();
        entity.costPrice = product.getCostPrice();
        entity.quantity = product.getQuantity();
        entity.lowStockThreshold = product.getLowStockThreshold();
        final var dims = product.getDimensions();
        if (dims != null) {
            entity.weight = dims.getWeight();
            entity.width = dims.getWidth();
            entity.height = dims.getHeight();
            entity.depth = dims.getDepth();
        }
        entity.status = product.getStatus();
        entity.featured = product.isFeatured();
        entity.createdAt = product.getCreatedAt();
        entity.updatedAt = product.getUpdatedAt();
        entity.deletedAt = product.getDeletedAt();

        product.getImages().forEach(img -> entity.images.add(ProductImageJpaEntity.from(img, entity)));
        return entity;
    }

    public Product toAggregate() {
        final var dims = ProductDimensions.of(this.weight, this.width, this.height, this.depth);
        final var imageAggregates = this.images.stream()
                .map(ProductImageJpaEntity::toAggregate)
                .toList();
        return Product.with(
                ProductId.from(this.id),
                this.categoryId != null ? CategoryId.from(this.categoryId) : null,
                this.brandId != null ? BrandId.from(this.brandId) : null,
                this.name, this.slug, this.description, this.shortDescription, this.sku,
                this.price, this.compareAtPrice, this.costPrice,
                this.quantity, this.lowStockThreshold, dims,
                this.status, this.featured,
                this.createdAt, this.updatedAt, this.deletedAt,
                this.version,
                imageAggregates
        );
    }

    public void updateFrom(final Product product) {
        this.categoryId = product.getCategoryId() != null ? product.getCategoryId().getValue() : null;
        this.brandId = product.getBrandId() != null ? product.getBrandId().getValue() : null;
        this.name = product.getName();
        this.slug = product.getSlug();
        this.description = product.getDescription();
        this.shortDescription = product.getShortDescription();
        this.sku = product.getSku();
        this.price = product.getPrice();
        this.compareAtPrice = product.getCompareAtPrice();
        this.costPrice = product.getCostPrice();
        this.quantity = product.getQuantity();
        this.lowStockThreshold = product.getLowStockThreshold();
        final var dims = product.getDimensions();
        if (dims != null) {
            this.weight = dims.getWeight();
            this.width = dims.getWidth();
            this.height = dims.getHeight();
            this.depth = dims.getDepth();
        }
        this.status = product.getStatus();
        this.featured = product.isFeatured();
        this.updatedAt = product.getUpdatedAt();
        this.deletedAt = product.getDeletedAt();

        // sync images: remove all and re-add (orphanRemoval handles deletions)
        this.images.clear();
        product.getImages().forEach(img -> this.images.add(ProductImageJpaEntity.from(img, this)));
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public void setCompareAtPrice(BigDecimal compareAtPrice) { this.compareAtPrice = compareAtPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getDepth() { return depth; }
    public void setDepth(BigDecimal depth) { this.depth = depth; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public List<ProductImageJpaEntity> getImages() { return images; }
    public void setImages(List<ProductImageJpaEntity> images) { this.images = images; }
}
