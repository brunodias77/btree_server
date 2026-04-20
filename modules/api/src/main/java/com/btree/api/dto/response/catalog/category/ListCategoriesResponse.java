package com.btree.api.dto.response.catalog.category;

import com.btree.application.usecase.catalog.category.list_all_categories.ListAllCategoriesOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record ListCategoriesResponse(
        String                       id,
        @JsonProperty("parent_id")   String  parentId,
        String                       name,
        String                       slug,
        String                       description,
        @JsonProperty("image_url")   String  imageUrl,
        @JsonProperty("sort_order")  int     sortOrder,
        boolean                      active,
        @JsonProperty("created_at")  Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<ListCategoriesResponse> children
) {
    public static ListCategoriesResponse from(final ListAllCategoriesOutput output){
        return new ListCategoriesResponse(
                output.id(),
                output.parentId(),
                output.name(),
                output.slug(),
                output.description(),
                output.imageUrl(),
                output.sortOrder(),
                output.active(),
                output.createdAt(),
                output.updatedAt(),
                output.children().stream()
                        .map(ListCategoriesResponse::from)
                        .toList()
        );
    }
}
