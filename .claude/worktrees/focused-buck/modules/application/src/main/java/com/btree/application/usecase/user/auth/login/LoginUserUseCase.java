package com.btree.application.usecase.user.auth.login;

import com.btree.domain.user.entity.LoginHistory;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import java.time.temporal.ChronoUnit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso #2 — Autenticação de usuário (login).
 *
 * <p>Retorna {@code Left(Notification)} quando as credenciais são inválidas ou a conta
 * está impedida; {@code Right(AuthenticateUserOutput)} com os tokens em caso de sucesso.
 *
 * <p>Quando o usuário tem 2FA ativo, retorna {@code Right(AuthenticateUserOutput)} com
 * {@code requiresTwoFactor=true} e um {@code transactionId}. O cliente deve completar
 * o fluxo via {@code POST /v1/auth/2fa/verify} (UC-11).
 *
 * <p>Proteção contra força bruta:
 * <ul>
 *   <li>Cada senha incorreta incrementa {@code accessFailedCount} e persiste o valor.</li>
 *   <li>Após {@value MAX_FAILED_ATTEMPTS} falhas consecutivas a conta é bloqueada por
 *       {@value LOCK_DURATION_MINUTES} minutos.</li>
 *   <li>Um login bem-sucedido zera o contador.</li>
 *   <li>Bloqueios expirados são removidos automaticamente no primeiro acesso.</li>
 * </ul>
 */
public class LoginUserUseCase implements UseCase<LoginUserCommand, LoginUserOutput> {

    public static final int  MAX_FAILED_ATTEMPTS   = 5;
    public static final long LOCK_DURATION_MINUTES = 15L;
    private static final long TWO_FACTOR_TOKEN_EXPIRATION_MINUTES = 5L;

    private final UserGateway _userGateway;
    private final SessionGateway _sessionGateway;
    private final UserTokenGateway _userTokenGateway;
    private final LoginHistoryGateway _loginHistoryGateway;
    private final PasswordHasher _passwordHasher;
    private final TokenProvider _tokenProvider;
    private final TokenHasher _tokenHasher;
    private final TransactionManager _transactionManager;
    private final DomainEventPublisher _eventPublisher;
    private final long _accessTokenExpirationMs;
    private final long _refreshTokenExpirationMs;

    public LoginUserUseCase(UserGateway _userGateway, SessionGateway _sessionGateway, UserTokenGateway _userTokenGateway, LoginHistoryGateway _loginHistoryGateway, PasswordHasher _passwordHasher, TokenProvider _tokenProvider, TokenHasher _tokenHasher, TransactionManager _transactionManager, DomainEventPublisher _eventPublisher, long _accessTokenExpirationMs, long _refreshTokenExpirationMs) {
        this._userGateway = _userGateway;
        this._sessionGateway = _sessionGateway;
        this._userTokenGateway = _userTokenGateway;
        this._loginHistoryGateway = _loginHistoryGateway;
        this._passwordHasher = _passwordHasher;
        this._tokenProvider = _tokenProvider;
        this._tokenHasher = _tokenHasher;
        this._transactionManager = _transactionManager;
        this._eventPublisher = _eventPublisher;
        this._accessTokenExpirationMs = _accessTokenExpirationMs;
        this._refreshTokenExpirationMs = _refreshTokenExpirationMs;
    }

    @Override
    public Either<Notification, LoginUserOutput> execute(LoginUserCommand loginUserCommand) {
        final var notification = Notification.create();
        final var deviceInfo = DeviceInfo.of(loginUserCommand.ipAddress(), loginUserCommand.userAgent());

        // 1. Buscar usuário pelo identificador (username ou email)
        final var userOtp = _userGateway.findByUsernameOrEmail(loginUserCommand.identifier().toLowerCase());
        if(userOtp.isEmpty()){
            return Left(notification);
        }

        final var user = userOtp.get();

        // 2. Verificar estado da conta

        if(!user.isEnabled()){
            return Left(notification);
        }

        if (user.isAccountLocked()) {
            // Auto-unlock se o prazo de bloqueio já expirou.
            if (user.getLockExpiresAt() != null && Instant.now().isAfter(user.getLockExpiresAt())) {
                user.unlockAccount();
                persistUserBestEffort(user);
            } else {
                notification.append(UserError.ACCOUNT_LOCKED);
                return Left(notification);
            }
        }

        // 3. Verificar senha
        if(!_passwordHasher.matches(loginUserCommand.rawPassword(), user.getPasswordHash())){
            recordFailedAttempt(user, deviceInfo, "Senha inválida");
            notification.append(UserError.INVALID_CREDENTIALS);
            return Left(notification);
        }

        // 4. Zerar contador de falhas em caso de sucesso
        if (user.getAccessFailedCount() > 0) {
            user.resetAccessFailed();
        }

        // 5. Se 2FA está ativo, gerar token temporário em vez de emitir tokens finais
        if (user.isTwoFactorEnabled()) {
            return handleTwoFactorRequired(user, deviceInfo);
        }

        // 6. Gerar tokens finais
        final var now = Instant.now();
        final var accessTokenExpiresAt  = now.plusMillis(_accessTokenExpirationMs);
        final var refreshTokenExpiresAt = now.plusMillis(_refreshTokenExpirationMs);


        final var accessToken = _tokenProvider.generate(
                user.getId().getValue().toString(),
                Map.of("username", user.getUsername(), "email", user.getEmail()),
                accessTokenExpiresAt
        );

        final var rawRefreshToken  = _tokenHasher.generate();
        final var refreshTokenHash = _tokenHasher.hash(rawRefreshToken);

        final var session = Session.create(
                user.getId(),
                refreshTokenHash,
                deviceInfo,
                refreshTokenExpiresAt,
                Notification.create()
        );
        final var history = LoginHistory.recordSuccess(user.getId(), deviceInfo, Notification.create());


        return Try(() -> _transactionManager.execute(() -> {
            _userGateway.update(user); // persiste o reset do accesssFailedCount
            _sessionGateway.create(session);
            _loginHistoryGateway.create(history);
            return new LoginUserOutput(
                    accessToken,
                    rawRefreshToken,
                    accessTokenExpiresAt,
                    user.getId().getValue().toString(),
                    user.getUsername(),
                    user.getEmail(),
                    false,
                    null
            );
        })).toEither().mapLeft(Notification::create);
    }

    /** Persiste o usuário sem lançar exceção (usado para desbloqueio automático). */
    private void persistUserBestEffort(final User user) {
        try {
            _transactionManager.execute(() -> {
                _userGateway.update(user);
                if (!user.getDomainEvents().isEmpty()) {
                    _eventPublisher.publishAll(user.getDomainEvents());
                }
                return null;
            });
        } catch (Exception ignored) {}
    }

    /**
     * Incrementa o contador de falhas, bloqueia a conta se o limiar for atingido e
     * persiste ambas as alterações atomicamente (best-effort: não propaga exceções).
     */
    private void recordFailedAttempt(final User user, final DeviceInfo deviceInfo, final String reason) {
        user.incrementAccessFailed();
        if (user.getAccessFailedCount() >= MAX_FAILED_ATTEMPTS) {
            user.lockAccount(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
        }
        try {
            _transactionManager.execute(() -> {
                _userGateway.update(user);
                _loginHistoryGateway.create(
                        LoginHistory.recordFailure(user.getId(), deviceInfo, reason, Notification.create())
                );
                if (!user.getDomainEvents().isEmpty()) {
                    _eventPublisher.publishAll(user.getDomainEvents());
                }
                return null;
            });
        } catch (Exception ignored) {
            // Best-effort: o registro de falha não deve impedir a resposta de credencial inválida
        }
    }

    // ── 2FA ──────────────────────────────────────────────────

    private Either<Notification, LoginUserOutput> handleTwoFactorRequired(
            final User user,
            final DeviceInfo deviceInfo
    ) {
        final var expiresAt   = Instant.now().plus(TWO_FACTOR_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        final var tokenHash   = _tokenHasher.hash(UUID.randomUUID().toString());
        final var twoFactorToken = UserToken.create(
                user.getId(),
                TokenType.TWO_FACTOR.name(),
                tokenHash,
                expiresAt
        );

        final UserId userId   = user.getId();
        final String  username = user.getUsername();
        final String  email    = user.getEmail();

        return Try(() -> _transactionManager.execute(() -> {
            _userGateway.update(user);  // persiste reset do accessFailedCount
            final var saved = _userTokenGateway.create(twoFactorToken);
            return new LoginUserOutput(
                    null,
                    null,
                    null,
                    userId.getValue().toString(),
                    username,
                    email,
                    true,
                    saved.getId().getValue().toString()
            );
        })).toEither().mapLeft(Notification::create);
    }
}
