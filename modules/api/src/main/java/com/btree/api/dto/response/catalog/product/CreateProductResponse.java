package com.btree.api.dto.response.catalog.product;


import com.btree.application.usecase.catalog.product.create.CreateProductOutput;
import com.btree.shared.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payload HTTP de resposta para {@code POST /api/v1/catalog/products} — 201 Created.
 */
public record CreateProductResponse(
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
        int quantity,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        ProductStatus status,
        boolean featured,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    /** Representação pública de uma imagem na resposta. */
    public record ImageResponse(String id, String url, String altText, int sortOrder, boolean primary) {}

    public static CreateProductResponse from(final CreateProductOutput output) {
        return new CreateProductResponse(
                output.id(),
                output.categoryId(),
                output.brandId(),
                output.name(),
                output.slug(),
                output.description(),
                output.shortDescription(),
                output.sku(),
                output.price(),
                output.compareAtPrice(),
                output.costPrice(),
                output.quantity(),
                output.lowStockThreshold(),
                output.weight(),
                output.width(),
                output.height(),
                output.depth(),
                output.status(),
                output.featured(),
                output.images().stream()
                        .map(i -> new ImageResponse(i.id(), i.url(), i.altText(), i.sortOrder(), i.primary()))
                        .toList(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}

