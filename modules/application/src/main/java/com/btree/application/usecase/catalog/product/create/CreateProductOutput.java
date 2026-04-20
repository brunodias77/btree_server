package com.btree.application.usecase.catalog.product.create;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.shared.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Output de UC-57 — CreateProduct.
 *
 * <p>Representa a visão pública do produto recém-criado. Não expõe
 * campos internos como {@code deletedAt} ou {@code version}.
 */
public record CreateProductOutput(
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
        List<ImageOutput> images,
        Instant createdAt,
        Instant updatedAt
) {
    /** Representação pública de uma imagem do produto. */
    public record ImageOutput(String id, String url, String altText, int sortOrder, boolean primary) {

        public static ImageOutput from(final ProductImage image) {
            return new ImageOutput(
                    image.getId().getValue().toString(),
                    image.getUrl(),
                    image.getAltText(),
                    image.getSortOrder(),
                    image.isPrimary()
            );
        }
    }

    public static CreateProductOutput from(final Product product) {
        final var dims = product.getDimensions();
        return new CreateProductOutput(
                product.getId().getValue().toString(),
                product.getCategoryId() != null ? product.getCategoryId().getValue().toString() : null,
                product.getBrandId() != null ? product.getBrandId().getValue().toString() : null,
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getShortDescription(),
                product.getSku(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getCostPrice(),
                product.getQuantity(),
                product.getLowStockThreshold(),
                dims != null ? dims.getWeight() : null,
                dims != null ? dims.getWidth() : null,
                dims != null ? dims.getHeight() : null,
                dims != null ? dims.getDepth() : null,
                product.getStatus(),
                product.isFeatured(),
                product.getImages().stream().map(ImageOutput::from).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
