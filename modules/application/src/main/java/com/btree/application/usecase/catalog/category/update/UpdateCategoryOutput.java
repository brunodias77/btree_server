package com.btree.application.usecase.catalog.category.update;

import com.btree.domain.catalog.entity.Category;

import java.time.Instant;

/**
 * Saída do caso de uso UC-46 — UpdateCategory.
 */
public record UpdateCategoryOutput(
        String  id,
        String  parentId,
        String  name,
        String  slug,
        String  description,
        String  imageUrl,
        int     sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static UpdateCategoryOutput from(final Category category) {
        return new UpdateCategoryOutput(
                category.getId().getValue().toString(),
                category.getParentId() != null ? category.getParentId().getValue().toString() : null,
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getSortOrder(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
