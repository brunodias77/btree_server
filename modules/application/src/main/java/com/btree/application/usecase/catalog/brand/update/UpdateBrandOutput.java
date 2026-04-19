package com.btree.application.usecase.catalog.brand.update;

import com.btree.domain.catalog.entity.Brand;

import java.time.Instant;

/**
 * Saída do caso de uso UC-53 — UpdateBrand.
 */
public record UpdateBrandOutput(
        String  id,
        String  name,
        String  slug,
        String  description,
        String  logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static UpdateBrandOutput from(final Brand brand) {
        return new UpdateBrandOutput(
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

