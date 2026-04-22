package com.btree.api.dto.response.catalog.brand;

import com.btree.application.usecase.catalog.brand.get_by_id.GetBrandByIdOutput;

import java.time.Instant;

public record GetBrandByIdResponse(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static GetBrandByIdResponse from(final GetBrandByIdOutput output) {
        return new GetBrandByIdResponse(
                output.id(),
                output.name(),
                output.slug(),
                output.description(),
                output.logoUrl(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}