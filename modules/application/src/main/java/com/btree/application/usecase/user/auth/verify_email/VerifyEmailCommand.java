package com.btree.application.usecase.user.auth.verify_email;

/**
 * Comando de entrada para verificação de e-mail.
 *
 * @param token token em texto claro recebido pelo usuário via e-mail
 */
public record VerifyEmailCommand(String token) {}

