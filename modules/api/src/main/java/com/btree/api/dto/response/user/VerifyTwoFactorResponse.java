package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.verify_two_factor.VerifyTwoFactorOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record VerifyTwoFactorResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("accessTokenExpiresAt") Instant accessTokenExpiresAt,
        @JsonProperty("userId") String userId,
        @JsonProperty("username") String username,
        @JsonProperty("email") String email
) {
    public static VerifyTwoFactorResponse from(final VerifyTwoFactorOutput output) {
        return new VerifyTwoFactorResponse(
                output.accessToken(),
                output.refreshToken(),
                output.accessTokenExpiresAt(),
                output.userId(),
                output.username(),
                output.email()
        );
    }
}