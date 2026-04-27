package com.btree.infrastructure.cart.event;


import com.btree.application.usecase.cart.create_for_user.CreateCartForUserCommand;
import com.btree.application.usecase.cart.create_for_user.CreateCartForUserUseCase;
import com.btree.shared.event.user.UserRegisteredIntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reage ao {@link UserRegisteredIntegrationEvent} publicado pelo módulo {@code users}
 * após o commit do registro, criando um carrinho ACTIVE para o novo usuário.
 *
 * <p>{@code AFTER_COMMIT} garante que o carrinho só é criado se o registro de usuário
 * foi persistido com sucesso. {@code REQUIRES_NEW} abre uma transação própria para que
 * uma falha aqui não afete o registro já commitado.
 */
@Component
public class UserRegisteredEventHandler {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventHandler.class);

    private final CreateCartForUserUseCase createCartForUserUseCase;

    public UserRegisteredEventHandler(final CreateCartForUserUseCase createCartForUserUseCase) {
        this.createCartForUserUseCase = createCartForUserUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(final UserRegisteredIntegrationEvent event) {
        log.debug("[cart] Criando carrinho para novo usuário userId={}", event.getUserId());

        createCartForUserUseCase
                .execute(new CreateCartForUserCommand(event.getUserId()))
                .peek(ignored ->
                        log.debug("[cart] Carrinho criado com sucesso para userId={}", event.getUserId()))
                .peekLeft(notification ->
                        log.error("[cart] Falha ao criar carrinho para userId={}: {}",
                                event.getUserId(), notification.getErrors()));
    }
}
