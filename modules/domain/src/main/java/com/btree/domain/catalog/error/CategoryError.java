package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class CategoryError {

    public static final Error CATEGORY_NOT_FOUND = new Error("Categoria não encontrada");
    public static final Error SLUG_ALREADY_EXISTS = new Error("Slug de categoria já está em uso");
    public static final Error CIRCULAR_REFERENCE = new Error("Referência circular detectada na hierarquia de categorias");

    // Validator errors
    public static final Error NAME_EMPTY = new Error("'name' não pode estar vazio");
    public static final Error NAME_TOO_LONG = new Error("'name' deve ter no máximo 200 caracteres");

    public static final Error SLUG_EMPTY = new Error("'slug' não pode estar vazio");
    public static final Error SLUG_TOO_LONG = new Error("'slug' deve ter no máximo 256 caracteres");
    public static final Error SLUG_INVALID_FORMAT = new Error("Formato de slug de categoria inválido");

    // Business rule errors
    public static final Error CATEGORY_ALREADY_DELETED = new Error("Categoria já foi excluída");
    public static final Error CANNOT_DELETE_WITH_PRODUCTS = new Error("Não é possível excluir uma categoria com produtos ativos");
    public static final Error PARENT_CATEGORY_NOT_FOUND = new Error("Categoria pai não encontrada ou foi removida");

    private CategoryError() {}
}
