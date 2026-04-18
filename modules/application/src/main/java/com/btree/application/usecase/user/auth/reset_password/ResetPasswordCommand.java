package com.btree.application.usecase.user.auth.reset_password;

/**
 * Input do caso de uso UC-07 — RequestPasswordReset.
 *
 * @param email endereço de e-mail do usuário que solicita a redefinição
 */
public record ResetPasswordCommand(String email) {
}
