package com.btree.application.usecase.catalog.product.create;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command de entrada para UC-57 — CreateProduct [CMD P0].
 *
 * <p>Todos os campos de identificação de categoria e marca são opcionais em DRAFT.
 * Dimensões físicas são opcionais e mapeadas para {@code ProductDimensions}.
 * Imagens iniciais são opcionais e mapeadas para {@code ProductImage}.
 */
public record CreateProductCommand(

        /** UUID da categoria (nullable — produto sem categoria é válido em DRAFT). */
        String categoryId,

        /** UUID da marca (nullable). */
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

        /** Peso em kg (nullable). */
        BigDecimal weight,
        /** Largura em cm (nullable). */
        BigDecimal width,
        /** Altura em cm (nullable). */
        BigDecimal height,
        /** Profundidade em cm (nullable). */
        BigDecimal depth,

        /** Lista de imagens iniciais (nullable ou vazia). Ordem no array determina sortOrder e primary. */
        List<ImageEntry> images

) {
    /** URL + texto alternativo. sortOrder e primary são calculados pelo UseCase pela posição na lista. */
    public record ImageEntry(String url, String altText) {}
}
