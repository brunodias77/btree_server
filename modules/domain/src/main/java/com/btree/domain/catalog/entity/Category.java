package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.events.CategoryCreatedEvent;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.validator.CategoryValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

public class Category extends AggregateRoot<CategoryId> {

    private CategoryId parentId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private int sortOrder;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Category(
            final CategoryId id,
            final CategoryId parentId,
            final String name,
            final String slug,
            final String description,
            final String imageUrl,
            final int sortOrder,
            final boolean active,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        super(id, 0);
        this.parentId = parentId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * Factory: creates a new Category.
     */
    public static Category create(
            final CategoryId parentId,
            final String name,
            final String slug,
            final String description,
            final String imageUrl,
            final int sortOrder
    ) {
        final var now = Instant.now();
        final var category = new Category(
                CategoryId.unique(),
                parentId,
                name, slug, description, imageUrl,
                sortOrder, true,
                now, now, null
        );

        final var notification = Notification.create();
        category.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        category.registerEvent(new CategoryCreatedEvent(
                category.getId().getValue().toString(),
                category.getName(),
                category.getSlug()
        ));

        return category;
    }

    /**
     * Factory: hydrates a Category from persistence.
     */
    public static Category with(
            final CategoryId id,
            final CategoryId parentId,
            final String name,
            final String slug,
            final String description,
            final String imageUrl,
            final int sortOrder,
            final boolean active,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        return new Category(id, parentId, name, slug, description, imageUrl, sortOrder, active, createdAt, updatedAt, deletedAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void update(
            final CategoryId parentId,
            final String name,
            final String slug,
            final String description,
            final String imageUrl,
            final int sortOrder
    ) {
        this.parentId = parentId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isRoot() {
        return this.parentId == null;
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new CategoryValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public CategoryId getParentId() { return parentId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
