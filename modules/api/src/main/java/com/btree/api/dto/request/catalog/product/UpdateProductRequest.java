package com.btree.api.dto.request.catalog.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload HTTP de entrada para {@code PATCH /v1/catalog/products/{id}}.
 *
 * <p>Todos os campos multi-palavra usam {@code @JsonProperty} snake_case para
 * consistência com o frontend Angular.
 */
public record UpdateProductRequest(

        @JsonProperty("category_id")
        String categoryId,

        @JsonProperty("brand_id")
        String brandId,

        @NotBlank
        @Size(max = 300)
        String name,

        @NotBlank
        @Size(max = 350)
        String slug,

        String description,

        @JsonProperty("short_description")
        @Size(max = 500)
        String shortDescription,

        @NotBlank
        @Size(max = 50)
        String sku,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal price,

        @JsonProperty("compare_at_price")
        @DecimalMin("0.00")
        BigDecimal compareAtPrice,

        @JsonProperty("cost_price")
        @DecimalMin("0.00")
        BigDecimal costPrice,

        @JsonProperty("low_stock_threshold")
        @Min(0)
        int lowStockThreshold,

        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,

        boolean featured
) {}
