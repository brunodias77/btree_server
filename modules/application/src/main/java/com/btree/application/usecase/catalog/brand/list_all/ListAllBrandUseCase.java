package com.btree.application.usecase.catalog.brand.list_all;

import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Right;

public class ListAllBrandUseCase implements QueryUseCase<ListAllBrandCommand, ListAllBrandOutput> {

    private final BrandGateway brandGateway;

    public ListAllBrandUseCase(final BrandGateway brandGateway) {
        this.brandGateway = brandGateway;
    }

    @Override
    public Either<Notification, ListAllBrandOutput> execute(final ListAllBrandCommand command) {
        return Right(ListAllBrandOutput.from(this.brandGateway.findAll()));
    }
}
