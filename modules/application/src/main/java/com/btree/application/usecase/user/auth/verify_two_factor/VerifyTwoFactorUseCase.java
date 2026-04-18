package com.btree.application.usecase.user.auth.verify_two_factor;

import com.btree.application.usecase.user.auth.login.LoginUserUseCase;
import com.btree.domain.user.entity.LoginHistory;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.*;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class VerifyTwoFactorUseCase implements UseCase<VerifyTwoFactorCommand, VerifyTwoFactorOutput> {

    private final UserTokenGateway userTokenGateway;
    private final UserGateway userGateway;
    private final SessionGateway sessionGateway;
    private final LoginHistoryGateway loginHistoryGateway;
    private final TotpGateway totpGateway;
    private final TokenProvider tokenProvider;
    private final TokenHasher tokenHasher;
    private final TransactionManager transactionManager;
    private final DomainEventPublisher eventPublisher;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public VerifyTwoFactorUseCase(UserTokenGateway userTokenGateway, UserGateway userGateway, SessionGateway sessionGateway, LoginHistoryGateway loginHistoryGateway, TotpGateway totpGateway, TokenProvider tokenProvider, TokenHasher tokenHasher, TransactionManager transactionManager, DomainEventPublisher eventPublisher, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this.userTokenGateway = userTokenGateway;
        this.userGateway = userGateway;
        this.sessionGateway = sessionGateway;
        this.loginHistoryGateway = loginHistoryGateway;
        this.totpGateway = totpGateway;
        this.tokenProvider = tokenProvider;
        this.tokenHasher = tokenHasher;
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    public Either<Notification, VerifyTwoFactorOutput> execute(VerifyTwoFactorCommand verifyTwoFactorCommand) {
        final var notification = Notification.create();

        // buscar o token de transacao 2FA
        UserTokenId tokenId;
        try {
            tokenId = UserTokenId.from(UUID.fromString(verifyTwoFactorCommand.transactionId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }


        final var tokenOpt = this.userTokenGateway.findById(tokenId);
        if (tokenOpt.isEmpty()) {
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }

        final var token = tokenOpt.get();

        // Validar tipo, expiração e uso do token (resposta genérica para não vazar info)
        if (!TokenType.TWO_FACTOR.name().equals(token.getTokenType())) {
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }

        if (token.isExpired() || token.isUsed()) {
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }


        //  Buscar usuário e verificar estado da conta
        final var userOpt = this.userGateway.findById(token.getUserId());
        if (userOpt.isEmpty()) {
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }

        final var user       = userOpt.get();
        final var deviceInfo = DeviceInfo.of(verifyTwoFactorCommand.ipAddress(), verifyTwoFactorCommand.userAgent());

        if (!user.isEnabled()) {
            notification.append(UserError.ACCOUNT_DISABLED);
            return Left(notification);
        }

        if (user.isAccountLocked()) {
            // Auto-unlock se o prazo de bloqueio já expirou
            if (user.getLockExpiresAt() != null && Instant.now().isAfter(user.getLockExpiresAt())) {
                user.unlockAccount();
                persistUserBestEffort(user);
            } else {
                notification.append(UserError.ACCOUNT_LOCKED);
                return Left(notification);
            }
        }

        //  Validar código TOTP
        if (!this.totpGateway.isValidCode(user.getTwoFactorSecret(), verifyTwoFactorCommand.code())) {
            recordFailedAttempt(user, deviceInfo, "Código TOTP inválido");
            notification.append(UserError.INVALID_TOTP_CODE);
            return Left(notification);
        }

        //  Gerar tokens finais e persistir em transação
        final var now = Instant.now();
        final var accessTokenExpiresAt  = now.plusMillis(accessTokenExpirationMs);
        final var refreshTokenExpiresAt = now.plusMillis(refreshTokenExpirationMs);


        final var accessToken = this.tokenProvider.generate(
                user.getId().getValue().toString(),
                Map.of("username", user.getUsername(), "email", user.getEmail()),
                accessTokenExpiresAt
        );

        final var rawRefreshToken  = this.tokenHasher.generate();
        final var refreshTokenHash = this.tokenHasher.hash(rawRefreshToken);

        final var session = Session.create(
                user.getId(),
                refreshTokenHash,
                deviceInfo,
                refreshTokenExpiresAt,
                Notification.create()
        );

        final var loginHistory = LoginHistory.recordSuccess(user.getId(), deviceInfo, Notification.create());

        if (user.getAccessFailedCount() > 0) {
            user.resetAccessFailed();
        }

        final var userId   = user.getId().getValue().toString();
        final var username = user.getUsername();
        final var email    = user.getEmail();

        return Try(() -> transactionManager.execute(() -> {
            token.markAsUsed();
            this.userTokenGateway.update(token);
            this.userGateway.update(user);  // persiste reset do accessFailedCount
            this.sessionGateway.create(session);
            this.loginHistoryGateway.create(loginHistory);
            return new VerifyTwoFactorOutput(
                    accessToken,
                    rawRefreshToken,
                    accessTokenExpiresAt,
                    userId,
                    username,
                    email
            );
        })).toEither().mapLeft(Notification::create);
    }

    // ── Brute-force helpers ───────────────────────────────────

    /**
     * Incrementa o contador de falhas, bloqueia a conta se o limiar for atingido e
     * persiste ambas as alterações atomicamente (best-effort: não propaga exceções).
     */
    private void recordFailedAttempt(final User user, final DeviceInfo deviceInfo, final String reason) {
        user.incrementAccessFailed();
        if (user.getAccessFailedCount() >= LoginUserUseCase.MAX_FAILED_ATTEMPTS) {
            user.lockAccount(
                    Instant.now().plus(LoginUserUseCase.LOCK_DURATION_MINUTES, java.time.temporal.ChronoUnit.MINUTES)
            );
        }
        try {
            this.transactionManager.execute(() -> {
                this.userGateway.update(user);
                this.loginHistoryGateway.create(
                        LoginHistory.recordFailure(user.getId(), deviceInfo, reason, Notification.create())
                );
                if (!user.getDomainEvents().isEmpty()) {
                    this.eventPublisher.publishAll(user.getDomainEvents());
                }
                return null;
            });
        } catch (Exception ignored) {
            // Best-effort: o registro de falha não deve impedir a resposta de código inválido
        }
    }

    /** Persiste o usuário sem lançar exceção (usado para desbloqueio automático). */
    private void persistUserBestEffort(final User user) {
        try {
            this.transactionManager.execute(() -> {
                this.userGateway.update(user);
                if (!user.getDomainEvents().isEmpty()) {
                    this.eventPublisher.publishAll(user.getDomainEvents());
                }
                return null;
            });
        } catch (Exception ignored) {}
    }
}
