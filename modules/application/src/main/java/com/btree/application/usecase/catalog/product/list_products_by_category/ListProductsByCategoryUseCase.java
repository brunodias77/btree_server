package com.btree.application.usecase.catalog.product.list_products_by_category;

import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Right;

/**
 * Caso de uso UC-64 — ListProductsByCategory [QRY P0].
 *
 * <p>Retorna página de produtos com {@code status = ACTIVE} e não-deletados
 * pertencentes a uma categoria específica.
 *
 * <p>Regras:
 * <ul>
 *   <li>Categoria inexistente ou soft-deletada resulta em {@code NotFoundException} (404).</li>
 *   <li>Produtos {@code INACTIVE}, {@code DRAFT}, {@code DISCONTINUED} ou soft-deletados são excluídos.</li>
 *   <li>Operação read-only — sem transação explícita.</li>
 * </ul>
 */
public class ListProductsByCategoryUseCase
        implements QueryUseCase<ListProductsByCategoryCommand, ListProductsByCategoryOutput> {

    private final ProductGateway  productGateway;
    private final CategoryGateway categoryGateway;

    public ListProductsByCategoryUseCase(
            final ProductGateway productGateway,
            final CategoryGateway categoryGateway
    ) {
        this.productGateway  = productGateway;
        this.categoryGateway = categoryGateway;
    }

    @Override
    public Either<Notification, ListProductsByCategoryOutput> execute(
            final ListProductsByCategoryCommand command) {

        // 1. Validar existência da categoria (pré-condição — fora do Either)
        final var category = categoryGateway.findById(CategoryId.from(command.categoryId()))
                .orElseThrow(() -> NotFoundException.with(CategoryError.CATEGORY_NOT_FOUND.message()));

        if (category.isDeleted()) {
            throw NotFoundException.with(CategoryError.CATEGORY_NOT_FOUND.message());
        }

        // 2. Buscar produtos ACTIVE da categoria com paginação
        final var pageRequest = PageRequest.of(command.page(), command.size());
        final var result = productGateway.findActiveByCategoryId(
                CategoryId.from(command.categoryId()), pageRequest);

        return Right(ListProductsByCategoryOutput.from(result));
    }
}
