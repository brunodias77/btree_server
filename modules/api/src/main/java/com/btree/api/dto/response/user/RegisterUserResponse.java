package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.register.RegisterUserOutput;

import java.time.Instant;

/**
 * DTO HTTP de saída para {@code POST /api/v1/auth/register}.
 *
 * <p>Mapeia {@link RegisterUserOutput} para a representação JSON da API.
 * Nunca expõe dados sensíveis (hash de senha, tokens internos).
 */
public record RegisterUserResponse(String userId, String username, String email, Instant createdAt) {

    public static RegisterUserResponse from(final RegisterUserOutput output) {
        return new RegisterUserResponse(
                output.userId(),
                output.username(),
                output.email(),
                output.createdAt()
        );
    }
}

