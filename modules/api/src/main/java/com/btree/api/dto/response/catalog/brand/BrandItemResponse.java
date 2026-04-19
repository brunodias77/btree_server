package com.btree.api.dto.response.catalog.brand;

import com.btree.application.usecase.catalog.brand.list_all.BrandOutputItem;

import java.time.Instant;

public record BrandItemResponse(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static BrandItemResponse from(final BrandOutputItem item) {
        return new BrandItemResponse(
                item.id(),
                item.name(),
                item.slug(),
                item.description(),
                item.logoUrl(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
