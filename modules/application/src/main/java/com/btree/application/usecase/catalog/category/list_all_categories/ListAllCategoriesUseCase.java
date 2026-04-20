package com.btree.application.usecase.catalog.category.list_all_categories;

import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.List;

import static io.vavr.API.Right;

public class ListAllCategoriesUseCase implements QueryUseCase<Void, List<ListAllCategoriesOutput>> {

    private final CategoryGateway categoryGateway;

    public ListAllCategoriesUseCase(CategoryGateway categoryGateway) {
        this.categoryGateway = categoryGateway;
    }

    @Override
    public Either<Notification, List<ListAllCategoriesOutput>> execute(Void unused) {
        final var categories = categoryGateway.findAll();
        return Right(ListAllCategoriesOutput.fromTree(categories));
    }
}
