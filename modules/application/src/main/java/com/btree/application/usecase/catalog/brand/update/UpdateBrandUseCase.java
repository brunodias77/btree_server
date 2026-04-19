package com.btree.application.usecase.catalog.brand.update;

import com.btree.domain.catalog.error.BrandError;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-53 — UpdateBrand [CMD P0].
 *
 * <p>Atualiza todos os campos mutáveis de uma marca existente (PUT semântico).
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>A marca deve existir e não estar soft-deletada.</li>
 *   <li>Se o slug mudar, deve ser único entre marcas não soft-deletadas.</li>
 * </ul>
 */
public class UpdateBrandUseCase implements UseCase<UpdateBrandCommand, UpdateBrandOutput> {

    private final BrandGateway brandGateway;
    private final TransactionManager transactionManager;

    public UpdateBrandUseCase(
            final BrandGateway brandGateway,
            final TransactionManager transactionManager
    ) {
        this.brandGateway       = brandGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateBrandOutput> execute(UpdateBrandCommand updateBrandCommand) {

        final var notification = Notification.create();

        // Resolver e validar o ID da marca
        final BrandId brandId;
        try {
            brandId = BrandId.from(UUID.fromString(updateBrandCommand.brandId()));
        } catch (IllegalArgumentException e) {
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }

        // Carregar a marca
        final var brandOpt = this.brandGateway.findById(brandId);
        if (brandOpt.isEmpty()) {
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }

        final var brand = brandOpt.get();

        // Rejeitar marcas soft-deletadas
        if (brand.isDeleted()) {
            notification.append(BrandError.BRAND_ALREADY_DELETED);
            return Left(notification);
        }

        // Unicidade do slug — excluindo a própria marca
        if (updateBrandCommand.slug() != null
                && !updateBrandCommand.slug().equals(brand.getSlug())
                && this.brandGateway.existsBySlugExcluding(updateBrandCommand.slug(), brandId)) {
            notification.append(BrandError.SLUG_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 5. Aplicar mutação no aggregate e persistir
        return Try(() -> transactionManager.execute(() -> {
            brand.update(
                    updateBrandCommand.name(),
                    updateBrandCommand.slug(),
                    updateBrandCommand.description(),
                    updateBrandCommand.logoUrl()
            );
            final var updated = brandGateway.update(brand);
            return UpdateBrandOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
