package com.btree.application.usecase.catalog.product.update;

import java.math.BigDecimal;

/**
 * Comando de entrada para UC-58 — UpdateProduct.
 */
public record UpdateProductCommand(
        String id,
        String categoryId,
        String brandId,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        boolean featured
) {}
