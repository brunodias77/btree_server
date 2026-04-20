package com.btree.application.usecase.catalog.product.create;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.value_object.ProductDimensions;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.domain.DomainException;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.List;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class CreateProductUseCase implements UseCase<CreateProductCommand, CreateProductOutput> {

    private final ProductGateway productGateway;
    private final DomainEventPublisher domainEventPublisher;
    private final TransactionManager transactionManager;

    public CreateProductUseCase(
            final ProductGateway productGateway,
            final DomainEventPublisher domainEventPublisher,
            final TransactionManager transactionManager
    ) {
        this.productGateway = productGateway;
        this.domainEventPublisher = domainEventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, CreateProductOutput> execute(final CreateProductCommand command) {
        final var notification = Notification.create();

        validateUniqueness(command, notification);
        if (notification.hasError()) {
            return Left(notification);
        }

        return Try(() -> transactionManager.execute(() -> {
            final var product = buildProduct(command);

            attachImages(command.images(), product, notification);
            if (notification.hasError()) {
                throw DomainException.with(notification.getErrors());
            }

            final var saved = productGateway.save(product);
            domainEventPublisher.publishAll(product.getDomainEvents());
            return CreateProductOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }

    // ── Private helpers ───────────────────────────────────────

    private void validateUniqueness(final CreateProductCommand command, final Notification notification) {
        if (command.slug() != null && productGateway.existsBySlug(command.slug())) {
            notification.append(ProductError.SLUG_ALREADY_EXISTS);
        }
        if (command.sku() != null && productGateway.existsBySku(command.sku())) {
            notification.append(ProductError.SKU_ALREADY_EXISTS);
        }
    }

    private Product buildProduct(final CreateProductCommand command) {
        final var categoryId = command.categoryId() != null ? CategoryId.from(command.categoryId()) : null;
        final var brandId    = command.brandId()    != null ? BrandId.from(command.brandId())       : null;
        final var dimensions = ProductDimensions.of(
                command.weight(), command.width(), command.height(), command.depth()
        );
        return Product.create(
                categoryId, brandId,
                command.name(), command.slug(),
                command.description(), command.shortDescription(),
                command.sku(),
                command.price(), command.compareAtPrice(), command.costPrice(),
                command.lowStockThreshold(),
                dimensions
        );
    }

    /**
     * Anexa as imagens ao produto respeitando a ordem da lista.
     * sortOrder é o índice na lista; apenas a primeira imagem é marcada como primary.
     * Falha rápido: para de processar ao primeiro erro de validação.
     */
    private void attachImages(
            final List<CreateProductCommand.ImageEntry> entries,
            final Product product,
            final Notification notification
    ) {
        if (entries == null || entries.isEmpty()) return;
        for (int i = 0; i < entries.size(); i++) {
            final var entry = entries.get(i);
            final var image = ProductImage.create(
                    product.getId(),
                    entry.url(),
                    entry.altText(),
                    i,
                    i == 0
            );
            product.addImage(image, notification);
            if (notification.hasError()) return;
        }
    }
}
