package com.btree.application.usecase.user.auth.login_social_provider;

import java.time.Instant;
import java.util.List;

/**
 * Output do caso de uso UC-09 — LoginWithSocialProvider.
 */
public record LoginSocialProviderOutput(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email,
        List<String> roles
) {
}
