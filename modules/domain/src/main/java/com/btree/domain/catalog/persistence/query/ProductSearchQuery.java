package com.btree.domain.catalog.persistence.query;

import com.btree.shared.enums.ProductStatus;

import java.math.BigDecimal;

/**
 * Critérios de busca de produtos — passado do use case para o gateway.
 * Todos os campos são opcionais (null = sem filtro).
 */
public record ProductSearchQuery(
        String term,           // busca trigram em name, sku, shortDescription
        String categoryId,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        Boolean featured
) {
    /** Critério vazio — retorna todos os produtos não deletados. */
    public static ProductSearchQuery empty() {
        return new ProductSearchQuery(null, null, null, null, null, null, null);
    }
}
