package com.btree.application.usecase.user.auth.register;

import com.btree.domain.user.entity.User;

import java.time.Instant;

/**
 * Saída do caso de uso {@link RegisterUserUseCase}.
 *
 * <p><b>Não contém</b>: passwordHash, tokens, dados sensíveis.
 *
 * @param userId    UUID do usuário criado (string)
 * @param username  username confirmado
 * @param email     e-mail cadastrado (normalizado para lowercase)
 * @param createdAt timestamp de criação
 */
public record RegisterUserOutput(String userId, String username, String email, Instant createdAt) {

    public static RegisterUserOutput from(final User user) {
        return new RegisterUserOutput(
                user.getId().getValue().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}

