package com.btree.application.usecase.catalog.product.adjust_stock;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.StockMovement;

/**
 * Output de UC-70 — AdjustStock.
 *
 * <p>Confirma o ajuste realizado retornando o ID do movimento gerado,
 * o saldo resultante e o status atualizado do produto.
 */
public record AdjustStockOutput(
        String movementId,
        String productId,
        String movementType,
        int delta,
        int quantityAfter,
        String productStatus,
        String createdAt
) {
    public static AdjustStockOutput from(final Product product, final StockMovement movement) {
        return new AdjustStockOutput(
                movement.getId().getValue().toString(),
                product.getId().getValue().toString(),
                movement.getMovementType().name(),
                movement.getQuantity(),
                product.getQuantity(),
                product.getStatus().name(),
                movement.getCreatedAt().toString()
        );
    }
}
