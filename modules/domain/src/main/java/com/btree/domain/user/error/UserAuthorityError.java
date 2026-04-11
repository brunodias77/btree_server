package com.btree.domain.user.error;
import com.btree.shared.validation.Error;


public final class UserAuthorityError {
    private UserAuthorityError() {}

    public static final Error USER_ID_NULL = new Error("'userId' não pode ser nulo");
    public static final Error AUTHORITY_NULL = new Error("'authority' não pode ser nulo");
}
