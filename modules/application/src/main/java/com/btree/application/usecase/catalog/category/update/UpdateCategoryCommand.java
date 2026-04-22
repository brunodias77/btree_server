package com.btree.application.usecase.catalog.category.update;


/**
 * Comando de entrada para UC-46 — UpdateCategory.
 *
 * <p>PUT semântico: o cliente envia o payload completo e todos os campos
 * mutáveis são substituídos.
 */
public record UpdateCategoryCommand(
        String categoryId,
        String parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        int    sortOrder
) {}
