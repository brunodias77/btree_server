package com.btree.api.dto.response.catalog.brand;

import com.btree.application.usecase.catalog.brand.create.CreateBrandOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída para {@code POST /api/v1/catalog/brands}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBrandResponse(
        String  id,
        String  name,
        String  slug,
        String  description,
        @JsonProperty("logo_url")   String  logoUrl,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static CreateBrandResponse from(final CreateBrandOutput output) {
        return new CreateBrandResponse(
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
