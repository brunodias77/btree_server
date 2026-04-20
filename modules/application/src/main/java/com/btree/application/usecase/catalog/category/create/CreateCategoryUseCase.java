package com.btree.application.usecase.catalog.category.create;

import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-45 — CreateCategory [CMD P0].
 *
 * <p>Cria uma nova categoria no catálogo, com suporte a hierarquia via {@code parentId}.
 * Omitir {@code parentId} cria uma categoria raiz.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O slug deve ser único entre categorias não soft-deletadas.</li>
 *   <li>Se {@code parentId} for informado, a categoria pai deve existir e não estar soft-deletada.</li>
 *   <li>A nova categoria é criada com {@code active = true} por padrão.</li>
 * </ul>
 *
 * <p>⚠️ {@code Category.create()} possui Notification interna e lança {@code DomainException}
 * se as invariantes falharem. Por isso, é chamado dentro do bloco {@code Try}, onde qualquer
 * exceção é capturada e mapeada para {@code Left(Notification)}.
 */
public class CreateCategoryUseCase implements UseCase<CreateCategoryCommand, CreateCategoryOutput> {

    private final CategoryGateway categoryGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public CreateCategoryUseCase(
            final CategoryGateway categoryGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.categoryGateway   = categoryGateway;
        this.eventPublisher    = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, CreateCategoryOutput> execute(final CreateCategoryCommand command) {
        final var notification = Notification.create();

        // 1. Unicidade do slug (apenas entre categorias não soft-deletadas)
        if (command.slug() != null && this.categoryGateway.existsBySlug(command.slug())) {
            notification.append(CategoryError.SLUG_ALREADY_EXISTS);
        }

        // 2. Validar parent quando informado
        CategoryId parentId = null;
        if (command.parentId() != null && !command.parentId().isBlank()) {
            try {
                final var candidateId = CategoryId.from(UUID.fromString(command.parentId()));
                final var parent = this.categoryGateway.findById(candidateId);
                if (parent.isEmpty() || parent.get().isDeleted()) {
                    notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
                } else {
                    parentId = candidateId;
                }
            } catch (IllegalArgumentException e) {
                notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Criar aggregate e persistir — Category.create() lança DomainException
        //    se as invariantes falharem; Try captura e converte para Left.
        final CategoryId resolvedParentId = parentId;

        return Try(() -> this.transactionManager.execute(() -> {
            final var category = Category.create(
                    resolvedParentId,
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.imageUrl(),
                    command.sortOrder()
            );
            final var saved = this.categoryGateway.save(category);
            this.eventPublisher.publishAll(category.getDomainEvents());
            return CreateCategoryOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }
}
