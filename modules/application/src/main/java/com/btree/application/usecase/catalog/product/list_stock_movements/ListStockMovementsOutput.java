package com.btree.application.usecase.catalog.product.list_stock_movements;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.pagination.Pagination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListStockMovementsOutput(
        List<MovementItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record MovementItem(
            String id,
            StockMovementType movementType,
            int quantity,
            UUID referenceId,
            String referenceType,
            String notes,
            Instant createdAt
    ) {
        public static MovementItem from(final StockMovement m) {
            return new MovementItem(
                    m.getId().getValue().toString(),
                    m.getMovementType(),
                    m.getQuantity(),
                    m.getReferenceId(),
                    m.getReferenceType(),
                    m.getNotes(),
                    m.getCreatedAt()
            );
        }
    }

    public static ListStockMovementsOutput from(final Pagination<StockMovement> page) {
        return new ListStockMovementsOutput(
                page.items().stream().map(MovementItem::from).toList(),
                page.currentPage(),
                page.perPage(),
                page.total(),
                page.totalPages()
        );
    }
}
