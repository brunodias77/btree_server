package com.btree.infrastructure.catalog.entity;

import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.identifier.BrandId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code catalog.brands}.
 * No version column (no optimistic locking for brands).
 */
@Entity
@Table(name = "brands", schema = "catalog")
public class BrandJpaEntity {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 256, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public BrandJpaEntity() {}

    public static BrandJpaEntity from(final Brand brand) {
        final var entity = new BrandJpaEntity();
        entity.id = brand.getId().getValue();
        entity.name = brand.getName();
        entity.slug = brand.getSlug();
        entity.description = brand.getDescription();
        entity.logoUrl = brand.getLogoUrl();
        entity.createdAt = brand.getCreatedAt();
        entity.updatedAt = brand.getUpdatedAt();
        entity.deletedAt = brand.getDeletedAt();
        return entity;
    }

    public Brand toAggregate() {
        return Brand.with(
                BrandId.from(this.id),
                this.name,
                this.slug,
                this.description,
                this.logoUrl,
                this.createdAt,
                this.updatedAt,
                this.deletedAt
        );
    }

    public void updateFrom(final Brand brand) {
        this.name = brand.getName();
        this.slug = brand.getSlug();
        this.description = brand.getDescription();
        this.logoUrl = brand.getLogoUrl();
        this.updatedAt = brand.getUpdatedAt();
        this.deletedAt = brand.getDeletedAt();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

}
