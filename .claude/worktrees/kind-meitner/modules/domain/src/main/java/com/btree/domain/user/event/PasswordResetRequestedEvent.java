package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

import java.time.Instant;

/**
 * Evento de domínio disparado quando o usuário solicita redefinição de senha.
 *
 * <p>Carrega o token em texto claro ({@code rawToken}) para que o consumidor
 * (e-mail transacional) possa montar o link de redefinição sem precisar acessar
 * o banco de dados.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Envio de e-mail com link de redefinição de senha</li>
 *   <li>Auditoria de solicitações de reset</li>
 * </ul>
 */
public class PasswordResetRequestedEvent extends DomainEvent {

    private final String userId;
    private final String email;
    private final String rawToken;
    private final Instant expiresAt;

    public PasswordResetRequestedEvent(
            final String userId,
            final String email,
            final String rawToken,
            final Instant expiresAt
    ) {
        super();
        this.userId = userId;
        this.email = email;
        this.rawToken = rawToken;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getAggregateId() {
        return userId;
    }

    @Override
    public String getAggregateType() {
        return "User";
    }

    @Override
    public String getEventType() {
        return "user.password_reset_requested";
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRawToken() { return rawToken; }
    public Instant getExpiresAt() { return expiresAt; }
}
