package com.btree.application.usecase.catalog.category.get_by_id;

import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

/**
 * Caso de uso UC-47 — GetCategory (Query).
 *
 * <p>Retorna os dados completos de uma categoria ativa pelo UUID.
 * Categorias soft-deletadas são tratadas como não encontradas.
 *
 * <p>Query pura — sem {@code TransactionManager}. A transação
 * read-only é gerenciada pelo {@code CategoryPostgresGateway}.
 */
public class GetCategoryUseCase implements QueryUseCase<GetCategoryCommand, GetCategoryOutput> {

    private final CategoryGateway categoryGateway;

    public GetCategoryUseCase(final CategoryGateway categoryGateway) {
        this.categoryGateway = categoryGateway;
    }

    @Override
    public Either<Notification, GetCategoryOutput> execute(final GetCategoryCommand command) {
        final var notification = Notification.create();

        final CategoryId categoryId;
        try {
            categoryId = CategoryId.from(UUID.fromString(command.categoryId()));
        } catch (IllegalArgumentException e) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        final var categoryOpt = categoryGateway.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        final var category = categoryOpt.get();

        // findById inclui soft-deletadas — checagem manual necessária
        if (category.isDeleted()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        return Right(GetCategoryOutput.from(category));
    }
}