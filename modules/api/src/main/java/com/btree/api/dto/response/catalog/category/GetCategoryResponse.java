package com.btree.api.dto.response.catalog.category;
import com.btree.application.usecase.catalog.category.get_by_id.GetCategoryOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída para {@code GET /api/v1/catalog/categories/{id}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetCategoryResponse(
        String  id,
        @JsonProperty("parent_id")  String  parentId,
        String  name,
        String  slug,
        String  description,
        @JsonProperty("image_url")  String  imageUrl,
        @JsonProperty("sort_order") int     sortOrder,
        boolean active,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static GetCategoryResponse from(final GetCategoryOutput output) {
        return new GetCategoryResponse(
                output.id(),
                output.parentId(),
                output.name(),
                output.slug(),
                output.description(),
                output.imageUrl(),
                output.sortOrder(),
                output.active(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}
