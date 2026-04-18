package com.btree.application.usecase.user.auth.setup_two_factor;

import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.TotpGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.StringEncryptor;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class SetupTwoFactorUseCase implements UseCase<SetupTwoFactorCommand, SetupTwoFactorOutput> {

    private static final long SETUP_TOKEN_EXPIRATION_MINUTES = 15L;
    private static final String TOTP_ISSUER = "BTree";

    private final UserGateway userGateway;
    private final UserTokenGateway userTokenGateway;
    private final TotpGateway totpGateway;
    private final StringEncryptor stringEncryptor;
    private final TransactionManager transactionManager;

    public SetupTwoFactorUseCase(
            final UserGateway userGateway,
            final UserTokenGateway userTokenGateway,
            final TotpGateway totpGateway,
            final StringEncryptor stringEncryptor,
            final TransactionManager transactionManager
    ) {
        this.userGateway = userGateway;
        this.userTokenGateway = userTokenGateway;
        this.totpGateway = totpGateway;
        this.stringEncryptor = stringEncryptor;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, SetupTwoFactorOutput> execute(final SetupTwoFactorCommand setupTwoFactorCommand) {
        final var notification = Notification.create();

        final var userId = UserId.from(UUID.fromString(setupTwoFactorCommand.userId()));
        final var userOpt = this.userGateway.findById(userId);

        if (userOpt.isEmpty()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var user = userOpt.get();

        if (user.isTwoFactorEnabled()) {
            notification.append(UserError.TWO_FACTOR_ALREADY_ENABLED);
            return Left(notification);
        }

        final String secret    = totpGateway.generateSecret();
        final String qrCodeUri = totpGateway.getUriForImage(secret, user.getEmail(), TOTP_ISSUER);

        // Secret criptografado em repouso no tokenHash do setup token
        final String encryptedSecret = stringEncryptor.encrypt(secret);

        final var expiresAt  = Instant.now().plus(SETUP_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        final var setupToken = UserToken.create(
                userId,
                TokenType.TWO_FACTOR_SETUP.name(),
                encryptedSecret,
                expiresAt
        );

        // Invalidar tokens de setup pendentes e criar o novo atomicamente
        return Try(() -> transactionManager.execute(() -> {
            userTokenGateway.invalidateActiveByUserIdAndType(userId, TokenType.TWO_FACTOR_SETUP.name());
            return userTokenGateway.create(setupToken);
        }))
                .toEither()
                .mapLeft(Notification::create)
                .map(saved -> new SetupTwoFactorOutput(
                        saved.getId().getValue().toString(),
                        secret,
                        qrCodeUri
                ));
    }
}
