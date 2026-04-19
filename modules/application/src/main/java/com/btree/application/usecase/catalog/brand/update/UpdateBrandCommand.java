package com.btree.application.usecase.catalog.brand.update;

/**
 * Comando de entrada para UC-53 — UpdateBrand.
 *
 * <p>PUT semântico: o cliente envia o payload completo e todos os campos
 * mutáveis são substituídos.
 */
public record UpdateBrandCommand(
        String brandId,
        String name,
        String slug,
        String description,
        String logoUrl
) {}
