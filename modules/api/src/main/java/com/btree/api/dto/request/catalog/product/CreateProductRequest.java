package com.btree.api.dto.request.catalog.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload HTTP de entrada para {@code POST /api/v1/catalog/products}.
 *
 * <p>Campos opcionais: {@code categoryId}, {@code brandId}, {@code description},
 * {@code shortDescription}, {@code compareAtPrice}, {@code costPrice},
 * dimensões físicas e {@code images}.
 */
public record CreateProductRequest(

        /** UUID da categoria — opcional em DRAFT. */
        String categoryId,

        /** UUID da marca — opcional em DRAFT. */
        String brandId,

        @NotBlank
        @Size(max = 300)
        String name,

        @NotBlank
        @Size(max = 350)
        String slug,

        String description,

        @Size(max = 500)
        String shortDescription,

        @NotBlank
        @Size(max = 50)
        String sku,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal price,

        @DecimalMin("0.00")
        BigDecimal compareAtPrice,

        @DecimalMin("0.00")
        BigDecimal costPrice,

        Integer lowStockThreshold,

        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,

        @Valid
        List<ProductImageRequest> images
) {}

