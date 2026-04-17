package com.btree.application.usecase.user.get_current_user;

/**
 * Input do caso de uso UC-05 — GetCurrentUser.
 *
 * @param userId ID do usuário autenticado, extraído do JWT pelo controller
 */
public record GetCurrentUserInput(String userId) {}
