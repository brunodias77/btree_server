package com.btree.application.usecase.user.auth.refresh_session;


import java.time.Instant;

/**
 * Saída do caso de uso de renovação de sessão.
 *
 * @param accessToken          novo JWT de curta duração
 * @param refreshToken         novo token opaco de longa duração (rotacionado)
 * @param accessTokenExpiresAt instante de expiração do novo access token
 * @param userId               UUID do usuário
 * @param username             username do usuário
 * @param email                e-mail do usuário
 */
public record RefreshSessionOutput(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email
) {}
