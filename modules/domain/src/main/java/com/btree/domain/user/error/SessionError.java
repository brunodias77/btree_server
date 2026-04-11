package com.btree.domain.user.error;
import com.btree.shared.validation.Error;


public final class SessionError {
    private SessionError() {}

    public static final Error USER_ID_NULL = new Error("'userId' não pode ser nulo");
    public static final Error REFRESH_TOKEN_EMPTY = new Error("'refreshTokenHash' não pode estar vazio");
    public static final Error EXPIRES_AT_NULL = new Error("'expiresAt' não pode ser nulo");
}
