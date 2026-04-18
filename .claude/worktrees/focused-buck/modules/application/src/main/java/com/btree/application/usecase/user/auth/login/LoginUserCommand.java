package com.btree.application.usecase.user.auth.login;

/**
 * Comando de entrada para autenticação de usuário.
 *
 * @param identifier username ou e-mail do usuário
 * @param rawPassword senha em texto claro
 * @param ipAddress   IP do cliente (pode ser null)
 * @param userAgent   User-Agent do cliente (pode ser null)
 */
public record LoginUserCommand(        String identifier,
                                       String rawPassword,
                                       String ipAddress,
                                       String userAgent
) {
}
