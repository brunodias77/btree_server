package com.btree.api.dto.response.catalog.brand;

import com.btree.application.usecase.catalog.brand.list_all.BrandOutputItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrandItemResponse(
        String id,
        String name,
        String slug,
        String description,
        @JsonProperty("logo_url")   String  logoUrl,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
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
