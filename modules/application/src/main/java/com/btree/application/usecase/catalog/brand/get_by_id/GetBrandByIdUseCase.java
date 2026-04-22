package com.btree.application.usecase.catalog.brand.get_by_id;

import com.btree.domain.catalog.error.BrandError;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

public class GetBrandByIdUseCase implements UseCase<GetBrandByIdCommand, GetBrandByIdOutput> {
    private final BrandGateway brandGateway;

    public GetBrandByIdUseCase(BrandGateway brandGateway) {
        this.brandGateway = brandGateway;
    }

    @Override
    public Either<Notification, GetBrandByIdOutput> execute(GetBrandByIdCommand command) {
        var notification = Notification.create();

        // validar se o brandid esta vazio ou null
        if(command.brandId().isBlank() || command.brandId() == null){
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }
        // criar um BrandId
        final BrandId brandId;
        try{
            brandId = BrandId.from(UUID.fromString(command.brandId()));
        }catch (IllegalArgumentException e){
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }
        // pesquisar no banco
        final var brandOpt = this.brandGateway.findById(brandId);
        if(brandOpt.isEmpty()){
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }
        // verificar se o brand nao esta deletado (soft-delete)
        final var brand = brandOpt.get();
        if(brand.isDeleted()){
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }
        return Right(GetBrandByIdOutput.from(brand));
    }
}
