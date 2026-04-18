package com.btree.application.usecase.user.auth.confirm_password_reset;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.validator.UserValidator;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class ConfirmPasswordResetUseCase implements UnitUseCase<ConfirmPasswordResetCommand> {

    private final UserTokenGateway userTokenGateway;
    private final UserGateway userGateway;
    private final TokenHasher tokenHasher;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public ConfirmPasswordResetUseCase(
            final UserTokenGateway userTokenGateway,
            final UserGateway userGateway,
            final TokenHasher tokenHasher,
            final PasswordHasher passwordHasher,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.userTokenGateway = userTokenGateway;
        this.userGateway = userGateway;
        this.tokenHasher = tokenHasher;
        this.passwordHasher = passwordHasher;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(final ConfirmPasswordResetCommand command) {
        final var notification = Notification.create();

        final var tokenHash = tokenHasher.hash(command.token());
        final var tokenOpt = userTokenGateway.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            notification.append(UserError.TOKEN_NOT_FOUND);
            return Left(notification);
        }

        final var userToken = tokenOpt.get();

        if (!TokenType.PASSWORD_RESET.name().equals(userToken.getTokenType())) {
            notification.append(UserError.TOKEN_INVALID_TYPE);
            return Left(notification);
        }
        if (userToken.isExpired()) {
            notification.append(UserError.TOKEN_EXPIRED);
            return Left(notification);
        }
        if (userToken.isUsed()) {
            notification.append(UserError.TOKEN_ALREADY_USED);
            return Left(notification);
        }

        final var userOpt = userGateway.findById(userToken.getUserId());
        if (userOpt.isEmpty()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var user = userOpt.get();

        UserValidator.validatePassword(command.newPassword(), notification);
        if (notification.hasError()) {
            return Left(notification);
        }

        final var newPasswordHash = passwordHasher.hash(command.newPassword());

        return Try(() -> transactionManager.execute(() -> {
            user.changePassword(newPasswordHash);
            userToken.markAsUsed();
            userGateway.update(user);
            userTokenGateway.update(userToken);
            eventPublisher.publishAll(user.getDomainEvents());
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
