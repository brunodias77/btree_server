package com.btree.api.dto.response.catalog.product;

import com.btree.application.usecase.catalog.product.adjust_stock.AdjustStockOutput;

/**
 * Payload HTTP de resposta para
 * {@code POST /api/v1/catalog/products/{productId}/stock/adjustments} — 201 Created.
 */
public record AdjustStockResponse(
        String movementId,
        String productId,
        String movementType,
        int delta,
        int quantityAfter,
        String productStatus,
        String createdAt
) {
    public static AdjustStockResponse from(final AdjustStockOutput output) {
        return new AdjustStockResponse(
                output.movementId(),
                output.productId(),
                output.movementType(),
                output.delta(),
                output.quantityAfter(),
                output.productStatus(),
                output.createdAt()
        );
    }
}
