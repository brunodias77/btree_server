package com.btree.domain.catalog.entity;

import com.btree.domain.catalog.events.BrandCreatedEvent;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.validator.BrandValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

/**
 * Aggregate Root — maps to {@code catalog.brands} table.
 *
 * <p>Soft delete via {@code deleted_at}. No optimistic locking (no version column in DB).
 */
public class Brand extends AggregateRoot<BrandId> {

    private String name;
    private String slug;
    private String description;
    private String logoUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Brand(
            final BrandId id,
            final String name,
            final String slug,
            final String description,
            final String logoUrl,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        super(id, 0);
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.logoUrl = logoUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * Factory: creates a new Brand.
     */
    public static Brand create(
            final String name,
            final String slug,
            final String description,
            final String logoUrl
    ) {
        final var now = Instant.now();
        final var brand = new Brand(
                BrandId.unique(),
                name, slug, description, logoUrl,
                now, now, null
        );

        final var notification = Notification.create();
        brand.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        brand.registerEvent(new BrandCreatedEvent(
                brand.getId().getValue().toString(),
                brand.getName(),
                brand.getSlug()
        ));

        return brand;
    }

    /**
     * Factory: hydrates a Brand from persistence.
     */
    public static Brand with(
            final BrandId id,
            final String name,
            final String slug,
            final String description,
            final String logoUrl,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        return new Brand(id, name, slug, description, logoUrl, createdAt, updatedAt, deletedAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void update(final String name, final String slug, final String description, final String logoUrl) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new BrandValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getLogoUrl() { return logoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}

