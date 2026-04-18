package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.login_social_provider.LoginSocialProviderOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record LoginSocialResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_token_expires_at") Instant accessTokenExpiresAt,
        @JsonProperty("user_id") String userId,
        String username,
        String email,
        List<String> roles
) {

    public static LoginSocialResponse from(final LoginSocialProviderOutput output) {
        return new LoginSocialResponse(
                output.accessToken(),
                output.refreshToken(),
                output.accessTokenExpiresAt(),
                output.userId(),
                output.username(),
                output.email(),
                output.roles()
        );
    }
}
