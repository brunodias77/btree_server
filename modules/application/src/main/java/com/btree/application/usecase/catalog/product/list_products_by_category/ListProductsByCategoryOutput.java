package com.btree.application.usecase.catalog.product.list_products_by_category;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.pagination.Pagination;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output de UC-64 — ListProductsByCategory.
 *
 * <p>Lista paginada de sumários leves de produtos ACTIVE de uma categoria.
 */
public record ListProductsByCategoryOutput(
        List<ProductSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record ProductSummary(
            String id,
            String name,
            String slug,
            String shortDescription,
            String sku,
            BigDecimal price,
            BigDecimal compareAtPrice,
            ProductStatus status,
            boolean featured,
            String primaryImageUrl
    ) {
        public static ProductSummary from(final Product product) {
            final var primaryImage = product.getImages().stream()
                    .filter(ProductImage::isPrimary)
                    .findFirst()
                    .orElse(null);
            return new ProductSummary(
                    product.getId().getValue().toString(),
                    product.getName(),
                    product.getSlug(),
                    product.getShortDescription(),
                    product.getSku(),
                    product.getPrice(),
                    product.getCompareAtPrice(),
                    product.getStatus(),
                    product.isFeatured(),
                    primaryImage != null ? primaryImage.getUrl() : null
            );
        }
    }

    public static ListProductsByCategoryOutput from(final Pagination<Product> page) {
        return new ListProductsByCategoryOutput(
                page.items().stream().map(ProductSummary::from).toList(),
                page.currentPage(),
                page.perPage(),
                page.total(),
                page.totalPages()
        );
    }
}
