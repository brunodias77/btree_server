package com.btree.shared.event.user;

import com.btree.shared.event.IntegrationEvent;

import java.util.UUID;

/**
 * Integration event publicado pelo módulo {@code users} após um novo usuário ser registrado
 * com sucesso.
 *
 * <p>Outros módulos (ex: {@code cart}) podem escutar este evento para reagir ao registro
 * sem que os módulos se conheçam diretamente. O evento trafega via
 * {@link com.btree.shared.event.IntegrationEventPublisher} e é despachado pelo
 * {@code SpringIntegrationEventPublisher} através do {@code ApplicationEventPublisher} do Spring.
 *
 * <p>Consumidores devem usar {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * + {@code @Transactional(propagation = REQUIRES_NEW)} para garantir que a reação
 * ocorre somente após o commit do registro e em transação própria.
 */
public final class UserRegisteredIntegrationEvent extends IntegrationEvent {

    private final UUID userId;
    private final String email;

    public UserRegisteredIntegrationEvent(final UUID userId, final String email) {
        super("users");
        this.userId = userId;
        this.email  = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getEventType() {
        return "user.registered";
    }
}
