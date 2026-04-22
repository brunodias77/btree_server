package com.btree.api.dto.request.catalog.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de uma imagem aninhada no {@link CreateProductRequest}.
 *
 * <p>Usa snake_case via {@code @JsonProperty} para corresponder ao frontend Angular.
 */
public record ProductImageRequest(

        @NotBlank
        @Size(max = 512)
        String url,

        @JsonProperty("alt_text")
        @Size(max = 256)
        String altText,

        @JsonProperty("sort_order")
        Integer sortOrder,

        Boolean primary
) {}
