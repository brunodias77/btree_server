package com.btree.application.usecase.catalog.product.get_by_id;

import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

/**
 * Caso de uso — GetProductById [QRY].
 *
 * <p>Retorna o produto completo (com imagens) pelo ID. Produtos soft-deleted
 * não são retornados ({@code findById} já filtra {@code deleted_at IS NULL}).
 * Retorna {@link ProductError#PRODUCT_NOT_FOUND} se não existir.
 */
public class GetProductByIdUseCase implements QueryUseCase<GetProductByIdCommand, GetProductByIdOutput> {

    private final ProductGateway productGateway;

    public GetProductByIdUseCase(final ProductGateway productGateway) {
        this.productGateway = productGateway;
    }

    @Override
    public Either<Notification, GetProductByIdOutput> execute(final GetProductByIdCommand command) {
        final var productId = ProductId.from(command.id());
        final var product   = productGateway.findById(productId);

        if (product.isEmpty()) {
            final var notification = Notification.create();
            notification.append(ProductError.PRODUCT_NOT_FOUND);
            return Left(notification);
        }

        return Right(GetProductByIdOutput.from(product.get()));
    }
}
