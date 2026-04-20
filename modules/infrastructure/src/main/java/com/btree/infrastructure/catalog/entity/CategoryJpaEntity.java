package com.btree.infrastructure.catalog.entity;



import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.identifier.CategoryId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.categories}.
 *
 * <p>Self-referencing hierarchy: {@code parent_id} is stored as a plain UUID
 * (not a JPA relationship) to avoid N+1 loading when building category trees.
 * No version column (no optimistic locking for categories).
 */
@Entity
@Table(name = "categories", schema = "catalog")
public class CategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 256, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CategoryJpaEntity() {}

    public static CategoryJpaEntity from(final Category category) {
        final var entity = new CategoryJpaEntity();
        entity.id = category.getId().getValue();
        entity.parentId = category.getParentId() != null ? category.getParentId().getValue() : null;
        entity.name = category.getName();
        entity.slug = category.getSlug();
        entity.description = category.getDescription();
        entity.imageUrl = category.getImageUrl();
        entity.sortOrder = category.getSortOrder();
        entity.active = category.isActive();
        entity.createdAt = category.getCreatedAt();
        entity.updatedAt = category.getUpdatedAt();
        entity.deletedAt = category.getDeletedAt();
        return entity;
    }

    public Category toAggregate() {
        return Category.with(
                CategoryId.from(this.id),
                this.parentId != null ? CategoryId.from(this.parentId) : null,
                this.name,
                this.slug,
                this.description,
                this.imageUrl,
                this.sortOrder,
                this.active,
                this.createdAt,
                this.updatedAt,
                this.deletedAt
        );
    }

    public void updateFrom(final Category category) {
        this.parentId = category.getParentId() != null ? category.getParentId().getValue() : null;
        this.name = category.getName();
        this.slug = category.getSlug();
        this.description = category.getDescription();
        this.imageUrl = category.getImageUrl();
        this.sortOrder = category.getSortOrder();
        this.active = category.isActive();
        this.updatedAt = category.getUpdatedAt();
        this.deletedAt = category.getDeletedAt();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
