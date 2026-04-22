package com.btree.application.usecase.catalog.brand.get_by_id;

import com.btree.domain.catalog.entity.Brand;

import java.time.Instant;

public record GetBrandByIdOutput(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static GetBrandByIdOutput from(final Brand brand) {
        return new GetBrandByIdOutput(
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
