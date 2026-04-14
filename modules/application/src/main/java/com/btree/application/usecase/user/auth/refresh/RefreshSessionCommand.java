package com.btree.application.usecase.user.auth.refresh;

/**
 * Comando de entrada para renovação de sessão.
 *
 * @param refreshToken token opaco em texto claro recebido no login anterior
 * @param ipAddress    IP do cliente (pode ser null)
 * @param userAgent    User-Agent do cliente (pode ser null)
 */
public record RefreshSessionCommand(
        String refreshToken,
        String ipAddress,
        String userAgent
) {}
