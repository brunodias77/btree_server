package com.btree.application.usecase.cart.create_for_user;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Right;
import static io.vavr.API.Try;

public class CreateCartForUserUseCase implements UnitUseCase<CreateCartForUserCommand> {

    private final CartGateway cartGateway;
    private final DomainEventPublisher eventPublisher;

    public CreateCartForUserUseCase(
            final CartGateway cartGateway,
            final DomainEventPublisher eventPublisher
    ) {
        this.cartGateway   = cartGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Either<Notification, Void> execute(final CreateCartForUserCommand command) {
        // Idempotent: do nothing if an active cart already exists
        if (cartGateway.findActiveByUserId(command.userId()).isPresent()) {
            return Right(null);
        }

        return Try(() -> {
            final var cart = Cart.createForUser(command.userId());
            cartGateway.save(cart);
            eventPublisher.publishAll(cart.getDomainEvents());
            return (Void) null;
        }).toEither().mapLeft(Notification::create);
    }
}
