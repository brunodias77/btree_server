package com.btree.application.usecase.catalog.product.list_stock_movements;

import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Right;

/**
 * UC — ListStockMovements [QRY].
 *
 * <p>Retorna o histórico paginado de movimentações de estoque de um produto.
 * Retorna 404 se o produto não existir ou estiver deletado.
 */
public class ListStockMovementsUseCase
        implements QueryUseCase<ListStockMovementsCommand, ListStockMovementsOutput> {

    private final ProductGateway productGateway;
    private final StockMovementGateway stockMovementGateway;

    public ListStockMovementsUseCase(
            final ProductGateway productGateway,
            final StockMovementGateway stockMovementGateway
    ) {
        this.productGateway = productGateway;
        this.stockMovementGateway = stockMovementGateway;
    }

    @Override
    public Either<Notification, ListStockMovementsOutput> execute(final ListStockMovementsCommand command) {
        final var productId = ProductId.from(command.productId());

        productGateway.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND.message()));

        final var page = stockMovementGateway.findByProduct(productId, PageRequest.of(command.page(), command.size()));
        return Right(ListStockMovementsOutput.from(page));
    }
}
