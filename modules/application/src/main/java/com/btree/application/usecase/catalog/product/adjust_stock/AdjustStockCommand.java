package com.btree.application.usecase.catalog.product.adjust_stock;

/**
 * Command de entrada para UC-70 — AdjustStock.
 *
 * @param productId     ID do produto (UUID em String)
 * @param delta         quantidade a ajustar; positivo = entrada, negativo = saída; nunca zero
 * @param movementType  nome do enum {@code StockMovementType} (ex.: "ADJUSTMENT", "IN", "OUT")
 * @param notes         motivo ou observação do ajuste (opcional)
 * @param referenceId   UUID externo de referência como String (ex.: NF, OS) — opcional
 * @param referenceType descrição do sistema de origem do referenceId — opcional, máx. 50 chars
 */
public record AdjustStockCommand(
        String productId,
        int delta,
        String movementType,
        String notes,
        String referenceId,
        String referenceType
) {}
