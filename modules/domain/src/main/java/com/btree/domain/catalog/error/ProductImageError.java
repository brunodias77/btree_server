package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class ProductImageError {

    public static final Error IMAGE_NOT_FOUND = new Error("Imagem de produto não encontrada");

    // Validator errors
    public static final Error URL_EMPTY = new Error("'url' da imagem não pode estar vazio");
    public static final Error URL_TOO_LONG = new Error("'url' da imagem deve ter no máximo 512 caracteres");
    public static final Error ALT_TEXT_TOO_LONG = new Error("'altText' deve ter no máximo 256 caracteres");
    public static final Error SORT_ORDER_NEGATIVE = new Error("'sortOrder' não pode ser negativo");

    private ProductImageError() {}
}
