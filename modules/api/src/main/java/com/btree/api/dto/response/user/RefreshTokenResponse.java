package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.refresh.RefreshSessionOutput;

import java.time.Instant;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email
) {
    public static RefreshTokenResponse from(final RefreshSessionOutput output) {
        return new RefreshTokenResponse(
                output.accessToken(),
                output.refreshToken(),
                output.accessTokenExpiresAt(),
                output.userId(),
                output.username(),
                output.email()
        );
    }
}
