package com.btree.api.dto.response.catalog.product;

import com.btree.application.usecase.catalog.product.update.UpdateProductOutput;
import com.btree.shared.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payload HTTP de resposta para {@code PATCH /v1/catalog/products/{id}} — 200 OK.
 *
 * <p>Todos os campos multi-palavra usam snake_case via {@code @JsonProperty}
 * para consistência com os demais endpoints do catálogo.
 */
public record UpdateProductResponse(
        String                            id,
        @JsonProperty("category_id")     String        categoryId,
        @JsonProperty("brand_id")        String        brandId,
        String                            name,
        String                            slug,
        String                            description,
        @JsonProperty("short_description") String       shortDescription,
        String                            sku,
        BigDecimal                        price,
        @JsonProperty("compare_at_price") BigDecimal   compareAtPrice,
        @JsonProperty("cost_price")       BigDecimal   costPrice,
        int                               quantity,
        @JsonProperty("low_stock_threshold") int        lowStockThreshold,
        BigDecimal                        weight,
        BigDecimal                        width,
        BigDecimal                        height,
        BigDecimal                        depth,
        ProductStatus                     status,
        boolean                           featured,
        List<ImageResponse>               images,
        @JsonProperty("created_at")       Instant      createdAt,
        @JsonProperty("updated_at")       Instant      updatedAt
) {
    public record ImageResponse(
            String  id,
            String  url,
            @JsonProperty("alt_text")   String  altText,
            @JsonProperty("sort_order") int     sortOrder,
            boolean primary
    ) {}

    public static UpdateProductResponse from(final UpdateProductOutput output) {
        return new UpdateProductResponse(
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
