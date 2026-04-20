package com.btree.application.usecase.catalog.category.create;


import com.btree.domain.catalog.entity.Category;

import java.time.Instant;

/**
 * Saída do caso de uso UC-45 — CreateCategory.
 */
public record CreateCategoryOutput(
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
    public static CreateCategoryOutput from(final Category category) {
        return new CreateCategoryOutput(
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
