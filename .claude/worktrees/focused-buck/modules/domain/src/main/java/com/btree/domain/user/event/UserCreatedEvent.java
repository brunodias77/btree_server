package com.btree.domain.user.event;


import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um novo usuário é registrado com sucesso.
 *
 * <p>Persistido no outbox ({@code shared.domain_events}) e processado
 * assincronamente pelo job {@code ProcessDomainEventsUseCase}.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Envio de e-mail de boas-vindas</li>
 *   <li>Geração do token de verificação de e-mail (UC-06 VerifyEmail)</li>
 *   <li>Auditoria de criação de conta</li>
 * </ul>
 */
public class UserCreatedEvent extends DomainEvent {

    private final String userId;
    private final String username;
    private final String email;

    public UserCreatedEvent(final String userId, final String username, final String email) {
        super();
        this.userId = userId;
        this.username = username;
        this.email = email;
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
        return "user.created";
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}
