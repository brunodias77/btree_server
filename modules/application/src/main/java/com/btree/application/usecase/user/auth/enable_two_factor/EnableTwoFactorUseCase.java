package com.btree.application.usecase.user.auth.enable_two_factor;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.TotpGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.StringEncryptor;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;


public class EnableTwoFactorUseCase implements UseCase<EnableTwoFactorCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(EnableTwoFactorUseCase.class);

    private final UserGateway userGateway;
    private final UserTokenGateway userTokenGateway;
    private final TotpGateway totpGateway;
    private final StringEncryptor stringEncryptor;
    private final TransactionManager transactionManager;
    private final DomainEventPublisher eventPublisher;

    public EnableTwoFactorUseCase(
            final UserGateway userGateway,
            final UserTokenGateway userTokenGateway,
            final TotpGateway totpGateway,
            final StringEncryptor stringEncryptor,
            final TransactionManager transactionManager,
            final DomainEventPublisher eventPublisher
    ) {
        this.userGateway = userGateway;
        this.userTokenGateway = userTokenGateway;
        this.totpGateway = totpGateway;
        this.stringEncryptor = stringEncryptor;
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Either<Notification, Void> execute(final EnableTwoFactorCommand enableTwoFactorCommand) {

        final var notification = Notification.create();

        final var tokenId = UserTokenId.from(UUID.fromString(enableTwoFactorCommand.setupTokenId()));
        final var tokenOpt = this.userTokenGateway.findById(tokenId);

        if (tokenOpt.isEmpty()) {
            notification.append(UserError.TOKEN_NOT_FOUND);
            return Left(notification);
        }

        final var token = tokenOpt.get();

        if (!TokenType.TWO_FACTOR_SETUP.name().equals(token.getTokenType())) {
            notification.append(UserError.TOKEN_INVALID_TYPE);
            return Left(notification);
        }

        final var userId = UserId.from(UUID.fromString(enableTwoFactorCommand.userId()));
        if (!token.getUserId().getValue().equals(userId.getValue())) {
            notification.append(UserError.TOKEN_NOT_FOUND);
            return Left(notification);
        }

        if (token.isExpired()) {
            notification.append(UserError.TOKEN_EXPIRED);
            return Left(notification);
        }

        if (token.isUsed()) {
            notification.append(UserError.TOKEN_ALREADY_USED);
            return Left(notification);
        }

        // Buscar usuário antes de validar o código TOTP para falhar rápido
        final var userOpt = this.userGateway.findById(userId);
        if (userOpt.isEmpty()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var user = userOpt.get();

        final String secret = stringEncryptor.decrypt(token.getTokenHash());

        log.debug("[EnableTwoFactor] decrypted secret length={}, secret={}", secret.length(), secret);
        log.debug("[EnableTwoFactor] provided code={}", enableTwoFactorCommand.code());

        if (!this.totpGateway.isValidCode(secret, enableTwoFactorCommand.code())) {
            notification.append(UserError.INVALID_TOTP_CODE);
            return Left(notification);
        }

        return Try(() -> transactionManager.execute(() -> {
            user.enableTwoFactor(secret);
            token.markAsUsed();
            userGateway.update(user);
            userTokenGateway.update(token);
            eventPublisher.publishAll(user.getDomainEvents());
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
