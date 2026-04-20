package com.btree.domain.catalog.error;

import com.btree.shared.validation.Error;

public class UserFavoriteError {

    public static final Error FAVORITE_NOT_FOUND = new Error("Favorito não encontrado");
    public static final Error ALREADY_FAVORITED = new Error("Produto já está nos favoritos do usuário");

    private UserFavoriteError() {}
}
