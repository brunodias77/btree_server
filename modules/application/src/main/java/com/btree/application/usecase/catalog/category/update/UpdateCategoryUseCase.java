package com.btree.application.usecase.catalog.category.update;

import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-46 — UpdateCategory [CMD P0].
 *
 * <p>Atualiza todos os campos mutáveis de uma categoria existente (PUT semântico).
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>A categoria deve existir e não estar soft-deletada.</li>
 *   <li>Se o slug mudar, deve ser único entre categorias não soft-deletadas.</li>
 *   <li>Se {@code parentId} for informado, a categoria pai deve existir, não estar deletada
 *       e não ser a própria categoria (evita referência circular).</li>
 * </ul>
 */
public class UpdateCategoryUseCase implements UseCase<UpdateCategoryCommand, UpdateCategoryOutput> {

    private final CategoryGateway categoryGateway;
    private final TransactionManager transactionManager;

    public UpdateCategoryUseCase(
            final CategoryGateway categoryGateway,
            final TransactionManager transactionManager
    ) {
        this.categoryGateway    = categoryGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateCategoryOutput> execute(final UpdateCategoryCommand command) {
        final var notification = Notification.create();

        // 1. Resolver e validar o ID da categoria
        final CategoryId categoryId;
        try {
            categoryId = CategoryId.from(UUID.fromString(command.categoryId()));
        } catch (IllegalArgumentException e) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        // 2. Carregar a categoria
        final var categoryOpt = categoryGateway.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        final var category = categoryOpt.get();

        // 3. Rejeitar categorias soft-deletadas
        if (category.isDeleted()) {
            notification.append(CategoryError.CATEGORY_ALREADY_DELETED);
            return Left(notification);
        }

        // 4. Unicidade do slug — excluindo a própria categoria
        if (command.slug() != null
                && !command.slug().equals(category.getSlug())
                && categoryGateway.existsBySlugExcluding(command.slug(), categoryId)) {
            notification.append(CategoryError.SLUG_ALREADY_EXISTS);
        }

        // 5. Validar novo parent (quando informado)
        CategoryId newParentId = null;
        if (command.parentId() != null && !command.parentId().isBlank()) {
            try {
                final var candidateId = CategoryId.from(UUID.fromString(command.parentId()));

                if (candidateId.getValue().equals(categoryId.getValue())) {
                    notification.append(CategoryError.CIRCULAR_REFERENCE);
                } else {
                    final var parent = categoryGateway.findById(candidateId);
                    if (parent.isEmpty() || parent.get().isDeleted()) {
                        notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
                    } else {
                        newParentId = candidateId;
                    }
                }
            } catch (IllegalArgumentException e) {
                notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 6. Aplicar mutação no aggregate e persistir
        final CategoryId resolvedParentId = newParentId;

        return Try(() -> transactionManager.execute(() -> {
            category.update(
                    resolvedParentId,
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.imageUrl(),
                    command.sortOrder()
            );
            final var updated = categoryGateway.update(category);
            return UpdateCategoryOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
