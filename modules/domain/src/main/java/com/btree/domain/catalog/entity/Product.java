package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.events.ProductArchivedEvent;
import com.btree.domain.catalog.events.ProductCreatedEvent;
import com.btree.domain.catalog.events.ProductImageAddedEvent;
import com.btree.domain.catalog.events.ProductImageRemovedEvent;
import com.btree.domain.catalog.events.ProductOutOfStockEvent;
import com.btree.domain.catalog.events.ProductPausedEvent;
import com.btree.domain.catalog.events.ProductPublishedEvent;
import com.btree.domain.catalog.events.ProductUpdatedEvent;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductImageId;
import com.btree.domain.catalog.value_object.ProductDimensions;
import com.btree.domain.catalog.validator.ProductValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root — maps to {@code catalog.products} table.
 *
 * <p>Owns: {@link ProductImage} (list).
 * Optimistic locking via {@code version}. Soft delete via {@code deleted_at}.
 *
 * <p>Status lifecycle:
 * <pre>
 *   DRAFT → ACTIVE (publish)
 *   ACTIVE → INACTIVE (pause)
 *   ACTIVE / INACTIVE → DISCONTINUED (archive)
 *   ACTIVE → OUT_OF_STOCK (auto, when quantity reaches 0)
 *   OUT_OF_STOCK → ACTIVE (auto, when stock is replenished)
 * </pre>
 */
public class Product extends AggregateRoot<ProductId> {

    private CategoryId categoryId;
    private BrandId brandId;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;
    private String sku;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private BigDecimal costPrice;
    private int quantity;
    private int lowStockThreshold;
    private ProductDimensions dimensions;
    private ProductStatus status;
    private boolean featured;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final List<ProductImage> images = new ArrayList<>();

    private Product(
            final ProductId id,
            final CategoryId categoryId,
            final BrandId brandId,
            final String name,
            final String slug,
            final String description,
            final String shortDescription,
            final String sku,
            final BigDecimal price,
            final BigDecimal compareAtPrice,
            final BigDecimal costPrice,
            final int quantity,
            final int lowStockThreshold,
            final ProductDimensions dimensions,
            final ProductStatus status,
            final boolean featured,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt,
            final int version
    ) {
        super(id, version);
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.shortDescription = shortDescription;
        this.sku = sku;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.costPrice = costPrice;
        this.quantity = quantity;
        this.lowStockThreshold = lowStockThreshold;
        this.dimensions = dimensions;
        this.status = status;
        this.featured = featured;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * Factory: creates a new Product in DRAFT status.
     */
    public static Product create(
            final CategoryId categoryId,
            final BrandId brandId,
            final String name,
            final String slug,
            final String description,
            final String shortDescription,
            final String sku,
            final BigDecimal price,
            final BigDecimal compareAtPrice,
            final BigDecimal costPrice,
            final int lowStockThreshold,
            final ProductDimensions dimensions
    ) {
        final var now = Instant.now();
        final var product = new Product(
                ProductId.unique(),
                categoryId, brandId,
                name, slug, description, shortDescription,
                sku, price, compareAtPrice, costPrice,
                0, lowStockThreshold, dimensions,
                ProductStatus.DRAFT,
                false,
                now, now, null,
                0
        );

        final var notification = Notification.create();
        product.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        product.registerEvent(new ProductCreatedEvent(
                product.getId().getValue().toString(),
                product.getName(),
                product.getSku()
        ));

        return product;
    }

    /**
     * Factory: hydrates a Product from persistence.
     */
    public static Product with(
            final ProductId id,
            final CategoryId categoryId,
            final BrandId brandId,
            final String name,
            final String slug,
            final String description,
            final String shortDescription,
            final String sku,
            final BigDecimal price,
            final BigDecimal compareAtPrice,
            final BigDecimal costPrice,
            final int quantity,
            final int lowStockThreshold,
            final ProductDimensions dimensions,
            final ProductStatus status,
            final boolean featured,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt,
            final int version,
            final List<ProductImage> images
    ) {
        final var product = new Product(
                id, categoryId, brandId, name, slug, description, shortDescription,
                sku, price, compareAtPrice, costPrice, quantity, lowStockThreshold,
                dimensions, status, featured, createdAt, updatedAt, deletedAt, version
        );
        if (images != null) {
            product.images.addAll(images);
        }
        return product;
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void update(
            final CategoryId categoryId,
            final BrandId brandId,
            final String name,
            final String slug,
            final String description,
            final String shortDescription,
            final String sku,
            final BigDecimal price,
            final BigDecimal compareAtPrice,
            final BigDecimal costPrice,
            final int lowStockThreshold,
            final ProductDimensions dimensions,
            final boolean featured,
            final Notification notification
    ) {
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.shortDescription = shortDescription;
        this.sku = sku;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.costPrice = costPrice;
        this.lowStockThreshold = lowStockThreshold;
        this.dimensions = dimensions;
        this.featured = featured;
        this.updatedAt = Instant.now();

        new ProductValidator(this, notification).validate();

        if (!notification.hasError()) {
            registerEvent(new ProductUpdatedEvent(getId().getValue().toString()));
        }
    }

    public void publish(final Notification notification) {
        if (this.deletedAt != null) {
            notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
        }
        if (this.price == null || this.price.compareTo(BigDecimal.ZERO) < 0) {
            notification.append(ProductError.CANNOT_PUBLISH_WITHOUT_PRICE);
        }
        if (notification.hasError()) {
            return;
        }
        this.status = this.quantity > 0 ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK;
        this.updatedAt = Instant.now();
        registerEvent(new ProductPublishedEvent(getId().getValue().toString(), this.name));
    }

    public void pause(final Notification notification) {
        if (this.deletedAt != null) {
            notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
            return;
        }
        if (ProductStatus.INACTIVE.equals(this.status)) {
            notification.append(ProductError.PRODUCT_ALREADY_INACTIVE);
            return;
        }
        if (!ProductStatus.ACTIVE.equals(this.status)
                && !ProductStatus.OUT_OF_STOCK.equals(this.status)) {
            notification.append(ProductError.CANNOT_PAUSE_IN_CURRENT_STATUS);
            return;
        }
        this.status    = ProductStatus.INACTIVE;
        this.updatedAt = Instant.now();
        registerEvent(new ProductPausedEvent(getId().getValue().toString()));
    }

    public void archive(final Notification notification) {
        if (this.deletedAt != null) {
            notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
            return;
        }
        if (ProductStatus.DISCONTINUED.equals(this.status)) {
            notification.append(ProductError.PRODUCT_ALREADY_DISCONTINUED);
            return;
        }
        if (ProductStatus.DRAFT.equals(this.status)) {
            notification.append(ProductError.CANNOT_ARCHIVE_IN_CURRENT_STATUS);
            return;
        }
        this.status    = ProductStatus.DISCONTINUED;
        this.updatedAt = Instant.now();
        registerEvent(new ProductArchivedEvent(getId().getValue().toString(), this.sku));
    }

    public void addStock(final int delta) {
        if (delta <= 0) throw new IllegalArgumentException("'delta' deve ser positivo para adicionar estoque");
        final boolean wasOutOfStock = this.status == ProductStatus.OUT_OF_STOCK;
        this.quantity += delta;
        this.updatedAt = Instant.now();
        incrementVersion();
        if (wasOutOfStock && this.quantity > 0) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    public void deductStock(final int delta) {
        if (delta <= 0) throw new IllegalArgumentException("'delta' deve ser positivo para deduzir estoque");
        if (this.quantity < delta) {
            throw DomainException.with(ProductError.QUANTITY_NEGATIVE);
        }
        this.quantity -= delta;
        this.updatedAt = Instant.now();
        incrementVersion();
        if (this.quantity == 0 && this.status == ProductStatus.ACTIVE) {
            this.status = ProductStatus.OUT_OF_STOCK;
            registerEvent(new ProductOutOfStockEvent(getId().getValue().toString(), this.sku));
        }
    }

    public void reserveStock(final int qty) {
        if (qty <= 0) throw new IllegalArgumentException("'qty' deve ser positivo para reservar estoque");
        if (this.quantity < qty) {
            throw DomainException.with(ProductError.QUANTITY_NEGATIVE);
        }
        this.quantity -= qty;
        this.updatedAt = Instant.now();
        incrementVersion();
        if (this.quantity == 0 && this.status == ProductStatus.ACTIVE) {
            this.status = ProductStatus.OUT_OF_STOCK;
            registerEvent(new ProductOutOfStockEvent(getId().getValue().toString(), this.sku));
        }
    }

    private static final int MAX_IMAGES = 10;

    /** Adiciona uma nova imagem ao produto, acumulando erros em {@code notification}. */
    public void addImage(final ProductImage image, final Notification notification) {
        if (this.deletedAt != null) {
            notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
            return;
        }
        if (this.images.size() >= MAX_IMAGES) {
            notification.append(ProductError.PRODUCT_IMAGE_LIMIT_EXCEEDED);
            return;
        }
        final boolean urlAlreadyExists = this.images.stream()
                .anyMatch(img -> img.getUrl().equalsIgnoreCase(image.getUrl()));
        if (urlAlreadyExists) {
            notification.append(ProductError.PRODUCT_IMAGE_URL_ALREADY_EXISTS);
            return;
        }
        this.images.add(image);
        this.updatedAt = Instant.now();
        registerEvent(new ProductImageAddedEvent(
                getId().getValue().toString(), image.getId().getValue().toString()));
    }

    /** Remove uma imagem pelo ID, acumulando erros em {@code notification}. */
    public void removeImage(final ProductImageId imageId, final Notification notification) {
        final var image = this.images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElse(null);
        if (image == null) {
            notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
            return;
        }
        this.images.remove(image);
        this.updatedAt = Instant.now();
        registerEvent(new ProductImageRemovedEvent(
                getId().getValue().toString(), imageId.getValue().toString()));
    }

    /** Define uma imagem como primária, desmarcando as demais. */
    public void setPrimaryImage(final ProductImageId imageId, final Notification notification) {
        final boolean exists = this.images.stream()
                .anyMatch(img -> img.getId().equals(imageId));
        if (!exists) {
            notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
            return;
        }
        this.images.forEach(img -> {
            if (img.getId().equals(imageId)) {
                img.markAsPrimary();
            } else {
                img.unmarkAsPrimary();
            }
        });
        this.updatedAt = Instant.now();
    }

    /**
     * Reordena as imagens de acordo com a lista de IDs fornecida.
     * A posição na lista define o novo {@code sortOrder} (base 1).
     */
    public void reorderImages(final List<ProductImageId> orderedIds, final Notification notification) {
        if (orderedIds.size() != this.images.size()) {
            notification.append(ProductError.PRODUCT_IMAGE_REORDER_INCOMPLETE);
            return;
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            final var id = orderedIds.get(i);
            final var image = this.images.stream()
                    .filter(img -> img.getId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (image == null) {
                notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
                return;
            }
            image.update(image.getUrl(), image.getAltText(), i + 1);
        }
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        if (this.deletedAt != null) {
            throw DomainException.with(ProductError.PRODUCT_ALREADY_DELETED);
        }
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isLowStock() {
        return this.quantity > 0 && this.quantity <= this.lowStockThreshold;
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new ProductValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public CategoryId getCategoryId() { return categoryId; }
    public BrandId getBrandId() { return brandId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getShortDescription() { return shortDescription; }
    public String getSku() { return sku; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public int getQuantity() { return quantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public ProductDimensions getDimensions() { return dimensions; }
    public ProductStatus getStatus() { return status; }
    public boolean isFeatured() { return featured; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public List<ProductImage> getImages() { return Collections.unmodifiableList(images); }
}
