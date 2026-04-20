package com.btree.api.dto.response.catalog.product;

import com.btree.application.usecase.catalog.product.list_all.ListAllProductsOutput;
import com.btree.shared.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record ListAllProductsResponse(
        List<ProductSummaryResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages")    int  totalPages
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductSummaryResponse(
            String id,
            String name,
            String slug,
            @JsonProperty("short_description") String        shortDescription,
            String                              sku,
            BigDecimal                          price,
            @JsonProperty("compare_at_price")  BigDecimal    compareAtPrice,
            ProductStatus                       status,
            boolean                             featured,
            @JsonProperty("primary_image_url") String        primaryImageUrl
    ) {
        public static ProductSummaryResponse from(final ListAllProductsOutput.ProductSummary item) {
            return new ProductSummaryResponse(
                    item.id(),
                    item.name(),
                    item.slug(),
                    item.shortDescription(),
                    item.sku(),
                    item.price(),
                    item.compareAtPrice(),
                    item.status(),
                    item.featured(),
                    item.primaryImageUrl()
            );
        }
    }

    public static ListAllProductsResponse from(final ListAllProductsOutput output) {
        return new ListAllProductsResponse(
                output.items().stream().map(ProductSummaryResponse::from).toList(),
                output.page(),
                output.size(),
                output.totalElements(),
                output.totalPages()
        );
    }
}
