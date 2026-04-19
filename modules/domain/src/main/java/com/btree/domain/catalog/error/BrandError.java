package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class BrandError {

    public static final Error BRAND_NOT_FOUND = new Error("Marca não encontrada");
    public static final Error SLUG_ALREADY_EXISTS = new Error("Slug de marca já está em uso");

    // Validator errors
    public static final Error NAME_EMPTY = new Error("'name' não pode estar vazio");
    public static final Error NAME_TOO_LONG = new Error("'name' deve ter no máximo 200 caracteres");

    public static final Error SLUG_EMPTY = new Error("'slug' não pode estar vazio");
    public static final Error SLUG_TOO_LONG = new Error("'slug' deve ter no máximo 256 caracteres");
    public static final Error SLUG_INVALID_FORMAT = new Error("Formato de slug de marca inválido");

    // Business rule errors
    public static final Error BRAND_ALREADY_DELETED = new Error("Marca já foi excluída");
    public static final Error CANNOT_DELETE_WITH_PRODUCTS = new Error("Não é possível excluir uma marca com produtos ativos");

    private BrandError() {}
}

