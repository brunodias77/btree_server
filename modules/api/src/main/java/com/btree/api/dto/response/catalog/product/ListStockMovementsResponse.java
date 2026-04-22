package com.btree.api.dto.response.catalog.product;

import com.btree.application.usecase.catalog.product.list_stock_movements.ListStockMovementsOutput;
import com.btree.shared.enums.StockMovementType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListStockMovementsResponse(
        List<MovementItemResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages")    int  totalPages
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MovementItemResponse(
            String id,
            @JsonProperty("movement_type")  StockMovementType movementType,
            int quantity,
            @JsonProperty("reference_id")   UUID   referenceId,
            @JsonProperty("reference_type") String referenceType,
            String notes,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static MovementItemResponse from(final ListStockMovementsOutput.MovementItem item) {
            return new MovementItemResponse(
                    item.id(),
                    item.movementType(),
                    item.quantity(),
                    item.referenceId(),
                    item.referenceType(),
                    item.notes(),
                    item.createdAt()
            );
        }
    }

    public static ListStockMovementsResponse from(final ListStockMovementsOutput output) {
        return new ListStockMovementsResponse(
                output.items().stream().map(MovementItemResponse::from).toList(),
                output.page(),
                output.size(),
                output.totalElements(),
                output.totalPages()
        );
    }
}
