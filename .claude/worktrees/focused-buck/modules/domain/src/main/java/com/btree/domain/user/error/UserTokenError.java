package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public class UserTokenError {
    public static final Error INVALID_TOKEN = new Error("Token inválido ou ausente");
    public static final Error TOKEN_EXPIRED = new Error("Token expirado");
    public static final Error TOKEN_ALREADY_USED = new Error("Token já foi utilizado");

    private UserTokenError() {}
}