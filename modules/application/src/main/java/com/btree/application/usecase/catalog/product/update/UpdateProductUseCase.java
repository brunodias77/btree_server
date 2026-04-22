package com.btree.application.usecase.catalog.product.update;

import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.value_object.ProductDimensions;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-58 — UpdateProduct [CMD P0].
 *
 * <p>Edita os dados cadastrais de um produto existente.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O produto deve existir e não estar soft-deletado.</li>
 *   <li>Se o {@code slug} mudou, deve ser único entre produtos não deletados (excluindo o próprio).</li>
 *   <li>Se o {@code sku} mudou, deve ser único entre produtos não deletados (excluindo o próprio).</li>
 *   <li>Não altera {@code status}, {@code quantity}, {@code createdAt} ou {@code deletedAt}.</li>
 *   <li>Emite {@code ProductUpdatedEvent} após persistência bem-sucedida.</li>
 * </ul>
 */
public class UpdateProductUseCase implements UseCase<UpdateProductCommand, UpdateProductOutput> {

    private final ProductGateway productGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public UpdateProductUseCase(
            final ProductGateway productGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.productGateway     = productGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateProductOutput> execute(final UpdateProductCommand command) {
        final var notification = Notification.create();

        // 1. Carregar produto (NotFoundException = pré-condição, fora do Either)
        final var productId = ProductId.from(command.id());
        final var product = productGateway.findById(productId)
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND.message()));

        // 2. Verificar unicidade de slug (somente se o valor mudou)
        if (!product.getSlug().equals(command.slug())
                && productGateway.existsBySlugExcludingId(command.slug(), productId)) {
            notification.append(ProductError.SLUG_ALREADY_EXISTS);
        }

        // 3. Verificar unicidade de SKU (somente se o valor mudou)
        if (!product.getSku().equals(command.sku())
                && productGateway.existsBySkuExcludingId(command.sku(), productId)) {
            notification.append(ProductError.SKU_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 4. Resolver IDs opcionais
        final var categoryId = command.categoryId() != null
                ? CategoryId.from(command.categoryId()) : null;
        final var brandId = command.brandId() != null
                ? BrandId.from(command.brandId()) : null;

        // 5. Construir ProductDimensions (null-safe)
        final var dimensions = ProductDimensions.of(
                command.weight(), command.width(), command.height(), command.depth()
        );

        // 6. Aplicar mutação no aggregate (valida invariantes e registra evento)
        product.update(
                categoryId, brandId,
                command.name(), command.slug(),
                command.description(), command.shortDescription(),
                command.sku(),
                command.price(), command.compareAtPrice(), command.costPrice(),
                command.lowStockThreshold(), dimensions,
                command.featured(), notification
        );

        if (notification.hasError()) {
            return Left(notification);
        }

        // 7. Persistir e publicar eventos dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var updated = productGateway.update(product);
            eventPublisher.publishAll(product.getDomainEvents());
            return UpdateProductOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
