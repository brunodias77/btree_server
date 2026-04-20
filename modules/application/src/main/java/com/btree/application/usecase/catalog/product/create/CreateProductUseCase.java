package com.btree.application.usecase.catalog.product.create;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.value_object.ProductDimensions;
import com.btree.shared.contract.TransactionManager;
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

    public CreateProductUseCase(ProductGateway productGateway, DomainEventPublisher domainEventPublisher, TransactionManager transactionManager) {
        this.productGateway = productGateway;
        this.domainEventPublisher = domainEventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, CreateProductOutput> execute(CreateProductCommand command) {
        final var notification = Notification.create();

        // 1. Validações de unicidade — acumular todos os erros antes de retornar
        if (command.slug() != null && this.productGateway.existsBySlug(command.slug())) {
            notification.append(ProductError.SLUG_ALREADY_EXISTS);
        }
        if (command.sku() != null && this.productGateway.existsBySku(command.sku())) {
            notification.append(ProductError.SKU_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 2. Resolver IDs opcionais
        final var categoryId = command.categoryId() != null
                ? CategoryId.from(command.categoryId()) : null;
        final var brandId = command.brandId() != null
                ? BrandId.from(command.brandId()) : null;

        // 3. Criar aggregate + imagens e persistir dentro da transação.
        //    ProductDimensions.of() e Product.create() lançam exceção se validação falhar → Try captura.
        return Try(() -> this.transactionManager.execute(() -> {
            final var dimensions = ProductDimensions.of(
                    command.weight(), command.width(), command.height(), command.depth()
            );

            final var product = Product.create(
                    categoryId, brandId,
                    command.name(), command.slug(),
                    command.description(), command.shortDescription(),
                    command.sku(),
                    command.price(), command.compareAtPrice(), command.costPrice(),
                    command.lowStockThreshold(),
                    dimensions
            );

            // Anexar imagens iniciais (se fornecidas)
            if (command.images() != null && !command.images().isEmpty()) {
                resolveImages(command.images(), product).forEach(img -> product.addImage(img, notification));
            }

            final var saved = productGateway.save(product);
            this.domainEventPublisher.publishAll(product.getDomainEvents());
            return CreateProductOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }

    // ── Helpers ───────────────────────────────────────────────

    private List<ProductImage> resolveImages(
            final List<CreateProductCommand.ImageEntry> entries,
            final Product product
    ) {
        return entries.stream()
                .map(e -> ProductImage.create(
                        product.getId(),
                        e.url(),
                        e.altText(),
                        e.sortOrder(),
                        e.primary()
                ))
                .toList();
    }
}
