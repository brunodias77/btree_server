package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class ProductReviewError {

    public static final Error REVIEW_NOT_FOUND = new Error("Avaliação de produto não encontrada");
    public static final Error REVIEW_ALREADY_EXISTS = new Error("Usuário já avaliou este produto");

    // Validator errors
    public static final Error RATING_INVALID = new Error("'rating' deve ser entre 1 e 5");
    public static final Error TITLE_TOO_LONG = new Error("'title' deve ter no máximo 200 caracteres");
    public static final Error PRODUCT_ID_NULL = new Error("'productId' não pode ser nulo");
    public static final Error USER_ID_NULL = new Error("'userId' não pode ser nulo");

    // Business rule errors
    public static final Error REVIEW_ALREADY_DELETED = new Error("Avaliação já foi excluída");

    private ProductReviewError() {}
}
