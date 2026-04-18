package com.btree.application.usecase.user.auth.verify_two_factor;

import java.time.Instant;

/**
 * Saída do caso de uso UC-11 — VerifyTwoFactor.
 *
 * @param accessToken          JWT de curta duração para autenticação de requests
 * @param refreshToken         token opaco de longa duração para renovar o access token
 * @param accessTokenExpiresAt instante de expiração do access token
 * @param userId               UUID do usuário autenticado
 * @param username             username normalizado
 * @param email                e-mail normalizado
 */
public record VerifyTwoFactorOutput(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email
) {
}
