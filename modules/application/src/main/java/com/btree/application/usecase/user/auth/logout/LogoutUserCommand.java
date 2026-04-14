package com.btree.application.usecase.user.auth.logout;

/**
 * Input do caso de uso UC-04 — LogoutUser.
 *
 * @param refreshToken token opaco recebido no momento do login/refresh
 */
public record LogoutUserCommand(String refreshToken) {}
