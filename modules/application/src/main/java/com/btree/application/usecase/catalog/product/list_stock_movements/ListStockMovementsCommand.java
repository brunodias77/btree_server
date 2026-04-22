package com.btree.application.usecase.catalog.product.list_stock_movements;

public record ListStockMovementsCommand(
        String productId,
        int page,
        int size
) {}
