package com.btree.application.usecase.user.auth.enable_two_factor;

/**
 * Input para {@link EnableTwoFactorUseCase}.
 *
 * @param userId       ID do usuário autenticado (extraído do JWT pelo controller).
 * @param setupTokenId ID do {@code UserToken} retornado pelo {@code SetupTwoFactorUseCase}.
 * @param code         Código TOTP de 6 dígitos gerado pelo app autenticador.
 */
public record EnableTwoFactorCommand(
        String userId,
        String setupTokenId,
        String code
) {
}
