package com.btree.application.usecase.user.auth.login;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.LoginHistory;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.domain.DomainEvent;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoginUserUseCase")
class LoginUserUseCaseTest extends UseCaseTest {

    @Test
    @DisplayName("deve autenticar usuario e criar sessao")
    void shouldAuthenticateUserAndCreateSession() {
        final var user = validUser();
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var sessionGateway = new FakeSessionGateway();
        final var userTokenGateway = new FakeUserTokenGateway();
        final var loginHistoryGateway = new FakeLoginHistoryGateway();
        final var passwordHasher = new FakePasswordHasher();
        final var tokenProvider = new FakeTokenProvider();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(
                userGateway,
                sessionGateway,
                userTokenGateway,
                loginHistoryGateway,
                passwordHasher,
                tokenProvider,
                tokenHasher,
                new FakeDomainEventPublisher()
        );

        final var result = useCase.execute(command("BRUNO@EXAMPLE.COM", "StrongPassword123!"));

        assertTrue(result.isRight());
        assertEquals("access-token", result.get().accessToken());
        assertEquals("raw-refresh-token", result.get().refreshToken());
        assertEquals(user.getId().getValue().toString(), result.get().userId());
        assertEquals("brunodias", result.get().username());
        assertEquals("bruno@example.com", result.get().email());
        assertFalse(result.get().requiresTwoFactor());
        assertNull(result.get().transactionId());

        assertEquals("bruno@example.com", userGateway.findByUsernameOrEmailValue);
        assertEquals(1, passwordHasher.matchesCalls);
        assertEquals(1, tokenProvider.generateCalls);
        assertEquals(user.getId().getValue().toString(), tokenProvider.subject);
        assertEquals(Map.of("username", "brunodias", "email", "bruno@example.com"), tokenProvider.claims);
        assertEquals(1, tokenHasher.generateCalls);
        assertEquals(1, sessionGateway.createCalls);
        assertEquals(user.getId(), sessionGateway.createdSession.getUserId());
        assertEquals("hashed-raw-refresh-token", sessionGateway.createdSession.getRefreshTokenHash());
        assertEquals(1, loginHistoryGateway.createCalls);
        assertTrue(loginHistoryGateway.createdHistory.isSuccess());
        assertEquals(1, userGateway.updateCalls);
        assertEquals(0, userTokenGateway.createCalls);
    }

    @Test
    @DisplayName("deve registrar falha quando senha esta incorreta")
    void shouldRecordFailureWhenPasswordIsInvalid() {
        final var user = validUser();
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var sessionGateway = new FakeSessionGateway();
        final var loginHistoryGateway = new FakeLoginHistoryGateway();
        final var passwordHasher = new FakePasswordHasher();
        passwordHasher.matches = false;
        final var useCase = newUseCase(
                userGateway,
                sessionGateway,
                new FakeUserTokenGateway(),
                loginHistoryGateway,
                passwordHasher,
                new FakeTokenProvider(),
                new FakeTokenHasher(),
                new FakeDomainEventPublisher()
        );

        final var result = useCase.execute(command("brunodias", "wrong-password"));

        assertTrue(result.isLeft());
        assertEquals("Credenciais inválidas", firstError(result.getLeft()));
        assertEquals(1, user.getAccessFailedCount());
        assertEquals(1, userGateway.updateCalls);
        assertEquals(1, loginHistoryGateway.createCalls);
        assertFalse(loginHistoryGateway.createdHistory.isSuccess());
        assertEquals("Senha inválida", loginHistoryGateway.createdHistory.getFailureReason());
        assertEquals(0, sessionGateway.createCalls);
    }

    @Test
    @DisplayName("deve retornar erro sem side effects quando usuario nao existe")
    void shouldReturnLeftWithoutSideEffectsWhenUserDoesNotExist() {
        final var userGateway = new FakeUserGateway();
        final var sessionGateway = new FakeSessionGateway();
        final var loginHistoryGateway = new FakeLoginHistoryGateway();
        final var passwordHasher = new FakePasswordHasher();
        final var tokenProvider = new FakeTokenProvider();
        final var tokenHasher = new FakeTokenHasher();
        final var userTokenGateway = new FakeUserTokenGateway();
        final var useCase = newUseCase(
                userGateway,
                sessionGateway,
                userTokenGateway,
                loginHistoryGateway,
                passwordHasher,
                tokenProvider,
                tokenHasher,
                new FakeDomainEventPublisher()
        );

        final var result = useCase.execute(command("missing@example.com", "StrongPassword123!"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().isEmpty());
        assertEquals("missing@example.com", userGateway.findByUsernameOrEmailValue);
        assertEquals(0, passwordHasher.matchesCalls);
        assertEquals(0, tokenProvider.generateCalls);
        assertEquals(0, tokenHasher.generateCalls);
        assertEquals(0, sessionGateway.createCalls);
        assertEquals(0, userTokenGateway.createCalls);
        assertEquals(0, loginHistoryGateway.createCalls);
        assertEquals(0, userGateway.updateCalls);
    }

    @Test
    @DisplayName("deve exigir 2FA quando usuario possui dois fatores ativo")
    void shouldRequireTwoFactorWhenEnabled() {
        final var user = validUser();
        user.enableTwoFactor("totp-secret");
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var sessionGateway = new FakeSessionGateway();
        final var userTokenGateway = new FakeUserTokenGateway();
        final var loginHistoryGateway = new FakeLoginHistoryGateway();
        final var tokenProvider = new FakeTokenProvider();
        final var useCase = newUseCase(
                userGateway,
                sessionGateway,
                userTokenGateway,
                loginHistoryGateway,
                new FakePasswordHasher(),
                tokenProvider,
                new FakeTokenHasher(),
                new FakeDomainEventPublisher()
        );

        final var result = useCase.execute(command("brunodias", "StrongPassword123!"));

        assertTrue(result.isRight());
        assertTrue(result.get().requiresTwoFactor());
        assertNull(result.get().accessToken());
        assertNull(result.get().refreshToken());
        assertNull(result.get().accessTokenExpiresAt());
        assertNotNull(result.get().transactionId());
        assertEquals(user.getId().getValue().toString(), result.get().userId());
        assertEquals(1, userTokenGateway.createCalls);
        assertEquals(TokenType.TWO_FACTOR.name(), userTokenGateway.createdToken.getTokenType());
        assertEquals(user.getId(), userTokenGateway.createdToken.getUserId());
        assertEquals(1, userGateway.updateCalls);
        assertEquals(0, tokenProvider.generateCalls);
        assertEquals(0, sessionGateway.createCalls);
        assertEquals(0, loginHistoryGateway.createCalls);
    }

    private static LoginUserUseCase newUseCase(
            final UserGateway userGateway,
            final SessionGateway sessionGateway,
            final UserTokenGateway userTokenGateway,
            final LoginHistoryGateway loginHistoryGateway,
            final PasswordHasher passwordHasher,
            final TokenProvider tokenProvider,
            final TokenHasher tokenHasher,
            final DomainEventPublisher domainEventPublisher
    ) {
        return new LoginUserUseCase(
                userGateway,
                sessionGateway,
                userTokenGateway,
                loginHistoryGateway,
                passwordHasher,
                tokenProvider,
                tokenHasher,
                new ImmediateTransactionManager(),
                domainEventPublisher,
                900_000L,
                604_800_000L
        );
    }

    private static LoginUserCommand command(final String identifier, final String password) {
        return new LoginUserCommand(identifier, password, "127.0.0.1", "JUnit");
    }

    private static User validUser() {
        return User.create(
                "brunodias",
                "bruno@example.com",
                "HashedPassword123!",
                Notification.create()
        );
    }

    private static final class FakeUserGateway implements UserGateway {
        private User user;
        private String findByUsernameOrEmailValue;
        private int updateCalls;

        @Override
        public User save(final User user) {
            return user;
        }

        @Override
        public boolean existsByUsername(final String username) {
            return false;
        }

        @Override
        public boolean existsByEmail(final String email) {
            return false;
        }

        @Override
        public void assignRole(final UserId userId, final String roleName) {
        }

        @Override
        public Optional<User> findByEmail(final String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(final UserId id) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsernameOrEmail(final String identifier) {
            this.findByUsernameOrEmailValue = identifier;
            return Optional.ofNullable(user);
        }

        @Override
        public User update(final User user) {
            this.updateCalls++;
            this.user = user;
            return user;
        }
    }

    private static final class FakeSessionGateway implements SessionGateway {
        private int createCalls;
        private Session createdSession;

        @Override
        public Session create(final Session session) {
            this.createCalls++;
            this.createdSession = session;
            return session;
        }

        @Override
        public Session update(final Session session) {
            return session;
        }

        @Override
        public Optional<Session> findByRefreshTokenHash(final String refreshTokenHash) {
            return Optional.empty();
        }

        @Override
        public Optional<Session> revokeActiveByRefreshTokenHash(final String refreshTokenHash, final Instant now) {
            return Optional.empty();
        }

        @Override
        public int revokeAllByUserId(final UserId userId) {
            return 0;
        }
    }

    private static final class FakeUserTokenGateway implements UserTokenGateway {
        private int createCalls;
        private UserToken createdToken;

        @Override
        public UserToken create(final UserToken userToken) {
            this.createCalls++;
            this.createdToken = userToken;
            return userToken;
        }

        @Override
        public UserToken update(final UserToken userToken) {
            return userToken;
        }

        @Override
        public Optional<UserToken> findByTokenHash(final String tokenHash) {
            return Optional.empty();
        }

        @Override
        public Optional<UserToken> findById(final UserTokenId id) {
            return Optional.empty();
        }

        @Override
        public int deleteExpired(final int batchSize) {
            return 0;
        }
    }

    private static final class FakeLoginHistoryGateway implements LoginHistoryGateway {
        private int createCalls;
        private LoginHistory createdHistory;

        @Override
        public LoginHistory create(final LoginHistory loginHistory) {
            this.createCalls++;
            this.createdHistory = loginHistory;
            return loginHistory;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        private boolean matches = true;
        private int matchesCalls;

        @Override
        public String hash(final String rawPassword) {
            return "hashed-" + rawPassword;
        }

        @Override
        public boolean matches(final String rawPassword, final String hashedPassword) {
            this.matchesCalls++;
            assertEquals("HashedPassword123!", hashedPassword);
            return matches;
        }
    }

    private static final class FakeTokenProvider implements TokenProvider {
        private int generateCalls;
        private String subject;
        private Map<String, Object> claims;

        @Override
        public String generate(final String subject, final Map<String, Object> claims, final Instant expiresAt) {
            this.generateCalls++;
            this.subject = subject;
            this.claims = claims;
            return "access-token";
        }

        @Override
        public String extractSubject(final String token) {
            return null;
        }

        @Override
        public boolean isValid(final String token) {
            return false;
        }

        @Override
        public <T> T extractClaim(final String token, final String claimKey, final Class<T> type) {
            return null;
        }
    }

    private static final class FakeTokenHasher implements TokenHasher {
        private int generateCalls;

        @Override
        public String generate() {
            this.generateCalls++;
            return "raw-refresh-token";
        }

        @Override
        public String hash(final String token) {
            return "hashed-" + token;
        }
    }

    private static final class FakeDomainEventPublisher implements DomainEventPublisher {
        private final List<DomainEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publish(final DomainEvent event) {
            this.publishedEvents.add(event);
        }

        @Override
        public void publishAll(final List<? extends DomainEvent> events) {
            this.publishedEvents.addAll(events);
        }
    }

    private static final class ImmediateTransactionManager implements TransactionManager {
        @Override
        public <T> T execute(final Supplier<T> action) {
            return action.get();
        }

        @Override
        public void executeVoid(final Runnable action) {
            action.run();
        }
    }
}
