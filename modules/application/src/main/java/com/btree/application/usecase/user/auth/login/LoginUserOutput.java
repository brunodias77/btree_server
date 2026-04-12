package com.btree.application.usecase.user.auth.login;

import java.time.Instant;

/**
 * Saída do caso de uso de autenticação.
 *
 * <p>Quando {@code requiresTwoFactor = true}, os campos {@code accessToken},
 * {@code refreshToken} e {@code accessTokenExpiresAt} serão {@code null}.
 * O cliente deve completar o fluxo via {@code POST /v1/auth/2fa/verify}
 * usando o {@code transactionId} retornado.
 *
 * @param accessToken           JWT de curta duração (null quando 2FA é requerido)
 * @param refreshToken          token opaco de longa duração (null quando 2FA é requerido)
 * @param accessTokenExpiresAt  expiração do access token (null quando 2FA é requerido)
 * @param userId                UUID do usuário autenticado
 * @param username              username normalizado
 * @param email                 e-mail normalizado
 * @param requiresTwoFactor     true quando 2FA está ativo e o cliente deve verificar o código
 * @param transactionId         ID do token TWO_FACTOR temporário (não-null quando requiresTwoFactor = true)
 */
public record LoginUserOutput(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String userId,
        String username,
        String email,
        boolean requiresTwoFactor,
        String transactionId
) {}
