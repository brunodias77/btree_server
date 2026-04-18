package com.btree.application.usecase.user.auth.register;

/**
 * Comando de entrada para o caso de uso {@link RegisterUserUseCase}.
 *
 * @param username nome de usuário (1–256 chars, alfanumérico + {@code - _})
 * @param email    endereço de e-mail (1–256 chars, formato válido)
 * @param password senha em texto plano — será hasheada antes de qualquer persistência
 */
public record RegisterUserCommand(String username, String email, String password) {
}

