package com.btree.application.usecase.user.auth.refresh_session;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.error.AuthError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.util.Map;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class RefreshSessionUseCase implements UseCase<RefreshSessionCommand, RefreshSessionOutput> {

    private final SessionGateway _sessionGateway;
    private final UserGateway _userGateway;
    private final TokenProvider _tokenProvider;
    private final TokenHasher _tokenHasher;
    private final TransactionManager _transactionManager;
    private final long _accessTokenExpirationMs;
    private final long _refreshTokenExpirationMs;

    public RefreshSessionUseCase(SessionGateway _sessionGateway, UserGateway _userGateway, TokenProvider _tokenProvider, TokenHasher _tokenHasher, TransactionManager _transactionManager, long _accessTokenExpirationMs, long _refreshTokenExpirationMs) {
        this._sessionGateway = _sessionGateway;
        this._userGateway = _userGateway;
        this._tokenProvider = _tokenProvider;
        this._tokenHasher = _tokenHasher;
        this._transactionManager = _transactionManager;
        this._accessTokenExpirationMs = _accessTokenExpirationMs;
        this._refreshTokenExpirationMs = _refreshTokenExpirationMs;
    }

    @Override
    public Either<Notification, RefreshSessionOutput> execute(RefreshSessionCommand refreshSessionCommand) {
        final var notification = Notification.create();

        // 1. Gerar dados do novo access/refresh token fora da transação.
        final var tokenHash = _tokenHasher.hash(refreshSessionCommand.refreshToken());
        final var now = Instant.now();
        final var accessTokenExpiresAt = now.plusMillis(_accessTokenExpirationMs);
        final var refreshTokenExpiresAt = now.plusMillis(_refreshTokenExpirationMs);
        final var newRawRefreshToken = _tokenHasher.generate();
        final var newRefreshTokenHash = _tokenHasher.hash(newRawRefreshToken);

        // 2. Consumir o refresh token de forma atômica e criar a nova sessão.
        return Try(() -> _transactionManager.execute(() -> {
            final var sessionOpt = _sessionGateway.revokeActiveByRefreshTokenHash(tokenHash, now);

            if (sessionOpt.isEmpty()) {
                notification.append(AuthError.INVALID_REFRESH_TOKEN);
                return null;
            }

            final var session = sessionOpt.get();
            final var userOpt = _userGateway.findById(session.getUserId());
            if (userOpt.isEmpty()) {
                notification.append(UserError.USER_NOT_FOUND);
                return null;
            }

            final var user = userOpt.get();
            final var newAccessToken = _tokenProvider.generate(
                    user.getId().getValue().toString(),
                    Map.of("username", user.getUsername(), "email", user.getEmail()),
                    accessTokenExpiresAt
            );

            final var newSession = Session.create(
                    user.getId(),
                    newRefreshTokenHash,
                    session.getDeviceInfo(),
                    refreshTokenExpiresAt,
                    Notification.create()
            );
            _sessionGateway.create(newSession);

            return new RefreshSessionOutput(
                    newAccessToken,
                    newRawRefreshToken,
                    accessTokenExpiresAt,
                    user.getId().getValue().toString(),
                    user.getUsername(),
                    user.getEmail()
            );
        })).toEither()
                .mapLeft(Notification::create)
                .flatMap(output -> notification.hasError() ? Left(notification) : Either.right(output));
    }
}
