package com.btree.application.usecase.user.auth.refresh;

import com.btree.domain.user.entity.Session;
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

        // 1. Buscar sessão pelo hash do refresh token
        final var tokenHash = _tokenHasher.hash(refreshSessionCommand.refreshToken());
        final var sessionOpt = _sessionGateway.findByRefreshTokenHash(tokenHash);

        if (sessionOpt.isEmpty()) {
            notification.append(UserError.SESSION_NOT_FOUND);
            return Left(notification);
        }

        final var session = sessionOpt.get();

        // 2. Validar estado da sessão
        if (session.isRevoked()) {
            notification.append(UserError.SESSION_REVOKED);
            return Left(notification);
        }
        if (session.isExpired()) {
            notification.append(UserError.SESSION_EXPIRED);
            return Left(notification);
        }

        // 3. Buscar usuário para claims do novo access token
        final var userOpt = _userGateway.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var user = userOpt.get();

        // 4. Gerar novos tokens
        final var now = Instant.now();
        final var accessTokenExpiresAt = now.plusMillis(_accessTokenExpirationMs);
        final var refreshTokenExpiresAt = now.plusMillis(_refreshTokenExpirationMs);

        final var newAccessToken = _tokenProvider.generate(
                user.getId().getValue().toString(),
                Map.of("username", user.getUsername(), "email", user.getEmail()),
                accessTokenExpiresAt
        );

        final var newRawRefreshToken = _tokenHasher.generate();
        final var newRefreshTokenHash = _tokenHasher.hash(newRawRefreshToken);

        // 5. Criar nova sessão (mesmo dispositivo)
        final var newSession = Session.create(
                user.getId(),
                newRefreshTokenHash,
                session.getDeviceInfo(),
                refreshTokenExpiresAt,
                Notification.create()
        );

        // 6. Em transação: revogar sessão antiga e persistir nova
        return Try(() -> _transactionManager.execute(() -> {
            session.revoke();
            _sessionGateway.update(session);
            _sessionGateway.create(newSession);

            return new RefreshSessionOutput(
                    newAccessToken,
                    newRawRefreshToken,
                    accessTokenExpiresAt,
                    user.getId().getValue().toString(),
                    user.getUsername(),
                    user.getEmail()
            );
        })).toEither().mapLeft(Notification::create);
    }
}
