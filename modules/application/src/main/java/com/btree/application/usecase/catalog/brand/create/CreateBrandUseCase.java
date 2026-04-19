package com.btree.application.usecase.catalog.brand.create;

import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.error.BrandError;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class CreateBrandUseCase implements UseCase<CreateBrandCommand, CreateBrandOutput> {

    private final BrandGateway brandGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public CreateBrandUseCase(
            final BrandGateway brandGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.brandGateway       = brandGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, CreateBrandOutput> execute(CreateBrandCommand createBrandCommand) {
        final var notification = Notification.create();

        // Unicidade do slug (entre marcas não soft-deletadas)
        if (createBrandCommand.slug() != null && brandGateway.existsBySlug(createBrandCommand.slug())) {
            notification.append(BrandError.SLUG_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // Criar aggregate e persistir — Brand.create() lança DomainException
        //    se as invariantes falharem; Try captura e converte para Left.
        return Try(() -> this.transactionManager.execute(() -> {
            final var brand = Brand.create(
                    createBrandCommand.name(),
                    createBrandCommand.slug(),
                    createBrandCommand.description(),
                    createBrandCommand.logoUrl()
            );
            final var saved = this.brandGateway.save(brand);
            this.eventPublisher.publishAll(brand.getDomainEvents());
            return CreateBrandOutput.from(saved);
        })).toEither().mapLeft(Notification::create);

    }
}
