package com.btree.api.dto.request.catalog.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de uma imagem aninhada no {@link CreateProductRequest}.
 */
public record ProductImageRequest(

        @NotBlank
        @Size(max = 512)
        String url,

        @Size(max = 256)
        String altText,

        Integer sortOrder,

        Boolean primary
) {}

