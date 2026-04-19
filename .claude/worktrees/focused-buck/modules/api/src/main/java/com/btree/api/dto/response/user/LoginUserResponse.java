package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.login.LoginUserOutput;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginUserResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email,
        Boolean requiresTwoFactor,
        String transactionId
) {
    public static LoginUserResponse from(final LoginUserOutput output) {
        return new LoginUserResponse(
                output.accessToken(),
                output.refreshToken(),
                output.accessTokenExpiresAt(),
                output.userId(),
                output.username(),
                output.email(),
                output.requiresTwoFactor() ? true : null,
                output.transactionId()
        );
    }
}
