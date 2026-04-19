package com.btree.application.usecase.catalog.brand.create;

import com.btree.domain.catalog.entity.Brand;

import java.time.Instant;

/**
 * Saída do caso de uso UC-52 — CreateBrand.
 */
public record CreateBrandOutput(
        String  id,
        String  name,
        String  slug,
        String  description,
        String  logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static CreateBrandOutput from(final Brand brand) {
        return new CreateBrandOutput(
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

