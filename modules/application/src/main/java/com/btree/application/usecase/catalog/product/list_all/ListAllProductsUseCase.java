package com.btree.application.usecase.catalog.product.list_all;

import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Right;

/**
 * Caso de uso — ListAllProducts [QRY].
 *
 * <p>Retorna página de todos os produtos não-deletados, independente de status.
 * Operação read-only — sem transação explícita.
 */
public class ListAllProductsUseCase implements QueryUseCase<ListAllProductsCommand, ListAllProductsOutput> {

    private final ProductGateway productGateway;

    public ListAllProductsUseCase(final ProductGateway productGateway) {
        this.productGateway = productGateway;
    }

    @Override
    public Either<Notification, ListAllProductsOutput> execute(final ListAllProductsCommand command) {
        final var pageRequest = PageRequest.of(command.page(), command.size());
        return Right(ListAllProductsOutput.from(productGateway.findAll(pageRequest)));
    }
}
