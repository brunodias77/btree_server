package com.btree.api.dto.request.catalog.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload HTTP de entrada para
 * {@code POST /api/v1/catalog/products/{productId}/stock/adjustments}.
 */
public record AdjustStockRequest(

        @NotNull(message = "'delta' é obrigatório e deve ser diferente de zero")
        Integer delta,

        @NotBlank(message = "'movementType' é obrigatório")
        String movementType,

        @Size(max = 1000, message = "'notes' deve ter no máximo 1000 caracteres")
        String notes,

        String referenceId,

        @Size(max = 50, message = "'referenceType' deve ter no máximo 50 caracteres")
        String referenceType
) {}

