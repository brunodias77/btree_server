package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class ProductError {

    public static final Error PRODUCT_NOT_FOUND = new Error("Produto não encontrado");
    public static final Error SKU_ALREADY_EXISTS = new Error("SKU já está em uso");
    public static final Error SLUG_ALREADY_EXISTS = new Error("Slug já está em uso");

    // Validator errors
    public static final Error NAME_EMPTY = new Error("'name' não pode estar vazio");
    public static final Error NAME_TOO_LONG = new Error("'name' deve ter no máximo 300 caracteres");

    public static final Error SLUG_EMPTY = new Error("'slug' não pode estar vazio");
    public static final Error SLUG_TOO_LONG = new Error("'slug' deve ter no máximo 350 caracteres");
    public static final Error SLUG_INVALID_FORMAT = new Error("Formato de slug inválido");

    public static final Error SKU_EMPTY = new Error("'sku' não pode estar vazio");
    public static final Error SKU_TOO_LONG = new Error("'sku' deve ter no máximo 50 caracteres");
    public static final Error SKU_INVALID_FORMAT = new Error("'sku' contém caracteres inválidos");

    public static final Error PRICE_NULL = new Error("'price' não pode ser nulo");
    public static final Error PRICE_NEGATIVE = new Error("'price' não pode ser negativo");

    public static final Error QUANTITY_NEGATIVE = new Error("'quantity' não pode ser negativo");
    public static final Error LOW_STOCK_THRESHOLD_NEGATIVE = new Error("'lowStockThreshold' não pode ser negativo");

    public static final Error SHORT_DESCRIPTION_TOO_LONG = new Error("'shortDescription' deve ter no máximo 500 caracteres");

    // Business rule errors
    public static final Error CANNOT_PUBLISH_WITHOUT_PRICE = new Error("Não é possível publicar um produto sem preço definido");
    public static final Error CANNOT_PUBLISH_DELETED_PRODUCT = new Error("Não é possível publicar um produto excluído");
    public static final Error PRODUCT_ALREADY_DELETED = new Error("Produto já foi excluído");
    public static final Error PRODUCT_ALREADY_ACTIVE = new Error("Produto já está ativo");
    public static final Error PRODUCT_ALREADY_INACTIVE = new Error("Produto já está inativo.");
    public static final Error CANNOT_PAUSE_IN_CURRENT_STATUS = new Error("Produto não pode ser pausado no status atual. Apenas produtos ACTIVE ou OUT_OF_STOCK podem ser pausados.");
    public static final Error PRODUCT_ALREADY_DISCONTINUED = new Error("Produto já está descontinuado.");
    public static final Error CANNOT_ARCHIVE_IN_CURRENT_STATUS = new Error("Produto em DRAFT não pode ser arquivado. Apenas produtos publicados (ACTIVE, INACTIVE, OUT_OF_STOCK) podem ser descontinuados.");
    public static final Error CANNOT_MODIFY_DELETED_PRODUCT = new Error("Não é possível modificar um produto deletado.");
    public static final Error PRODUCT_IMAGE_NOT_FOUND = new Error("Imagem não encontrada neste produto.");
    public static final Error PRODUCT_IMAGE_URL_ALREADY_EXISTS = new Error("Já existe uma imagem com esta URL neste produto.");
    public static final Error PRODUCT_IMAGE_LIMIT_EXCEEDED = new Error("O produto já atingiu o limite máximo de 10 imagens.");
    public static final Error PRODUCT_IMAGE_REORDER_INCOMPLETE = new Error("A lista de reordenação deve conter exatamente todas as imagens do produto.");

    private ProductError() {}
}
