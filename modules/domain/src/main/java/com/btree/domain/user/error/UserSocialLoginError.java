package com.btree.domain.user.error;
import com.btree.shared.validation.Error;


public final class UserSocialLoginError {
    private UserSocialLoginError() {}

    public static final Error PROVIDER_EMPTY = new Error("'provider' não pode estar vazio");
    public static final Error PROVIDER_USER_ID_EMPTY = new Error("'providerUserId' não pode estar vazio");
}
