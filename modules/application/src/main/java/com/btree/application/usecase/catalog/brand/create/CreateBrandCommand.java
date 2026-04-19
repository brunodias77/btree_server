package com.btree.application.usecase.catalog.brand.create;


/**
 * Comando de entrada para UC-52 — CreateBrand.
 */
public record CreateBrandCommand(
        String name,
        String slug,
        String description,
        String logoUrl
) {}
