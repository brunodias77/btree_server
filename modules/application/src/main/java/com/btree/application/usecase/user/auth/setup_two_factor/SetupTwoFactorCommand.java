package com.btree.application.usecase.user.auth.setup_two_factor;

/**
 * Input para {@link SetupTwoFactorUseCase}.
 *
 * @param userId ID do usuário autenticado (extraído do JWT pelo controller).
 */
public record SetupTwoFactorCommand(String userId) {
}
