package com.btree.application.usecase.catalog.product.list_products_by_category;



/**
 * Comando de entrada para UC-64 — ListProductsByCategory.
 */
public record ListProductsByCategoryCommand(
        String categoryId,
        int page,
        int size
) {}
