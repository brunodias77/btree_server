package com.btree.application.usecase.user.auth.verify_two_factor;

/**
 * Entrada do caso de uso UC-11 — VerifyTwoFactor.
 *
 * @param transactionId ID do {@code UserToken} do tipo {@code TWO_FACTOR} retornado pelo login
 * @param code          código TOTP de 6 dígitos gerado pelo app autenticador
 * @param ipAddress     IP do cliente (pode ser null)
 * @param userAgent     User-Agent do cliente (pode ser null)
 */
public record VerifyTwoFactorCommand(
        String transactionId,
        String code,
        String ipAddress,
        String userAgent
) {
}
