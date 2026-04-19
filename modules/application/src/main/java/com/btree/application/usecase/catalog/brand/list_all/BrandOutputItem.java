package com.btree.application.usecase.catalog.brand.list_all;

import com.btree.domain.catalog.entity.Brand;

import java.time.Instant;

public record BrandOutputItem(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static BrandOutputItem from(final Brand brand) {
        return new BrandOutputItem(
                brand.getId().getValue().toString(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
