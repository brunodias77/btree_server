package com.btree.application.usecase.catalog.product.list_all;

public record ListAllProductsCommand(
        int page,
        int size
) {}
