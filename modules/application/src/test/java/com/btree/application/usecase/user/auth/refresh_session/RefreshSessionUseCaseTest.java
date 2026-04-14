package com.btree.application.usecase.user.auth.refresh_session;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RefreshSessionUseCase")
class RefreshSessionUseCaseTest extends UseCaseTest {

    @Test
    @DisplayName("deve rotacionar refresh token e criar nova sessao")
    void shouldRotateRefreshTokenAndCreateNewSession() {
        final var user = validUser();
        final var consumedSession = consumedSession(user.getId());
        final var sessionGateway = new FakeSessionGateway();
        sessionGateway.sessionToConsume = consumedSession;
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var tokenProvider = new FakeTokenProvider();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, userGateway, tokenProvider, tokenHasher);

        final var result = useCase.execute(new RefreshSessionCommand(
                "old-refresh-token",
                "127.0.0.1",
                "JUnit"
        ));

        assertTrue(result.isRight());
        assertEquals("new-access-token", result.get().accessToken());
        assertEquals("new-refresh-token", result.get().refreshToken());
        assertEquals(user.getId().getValue().toString(), result.get().userId());
        assertEquals("brunodias", result.get().username());
        assertEquals("bruno@example.com", result.get().email());
        assertNotNull(result.get().accessTokenExpiresAt());

        assertEquals("hashed-old-refresh-token", sessionGateway.revokeRefreshTokenHashValue);
        assertNotNull(sessionGateway.revokeNowValue);
        assertEquals(1, userGateway.findByIdCalls);
        assertEquals(user.getId(), userGateway.findByIdValue);
        assertEquals(1, tokenProvider.generateCalls);
        assertEquals(user.getId().getValue().toString(), tokenProvider.subject);
        assertEquals(Map.of("username", "brunodias", "email", "bruno@example.com"), tokenProvider.claims);
        assertEquals(1, sessionGateway.createCalls);
        assertEquals(user.getId(), sessionGateway.createdSession.getUserId());
        assertEquals("hashed-new-refresh-token", sessionGateway.createdSession.getRefreshTokenHash());
        assertEquals(consumedSession.getDeviceInfo(), sessionGateway.createdSession.getDeviceInfo());
    }

    @Test
    @DisplayName("deve retornar erro quando refresh token ja foi consumido ou esta invalido")
    void shouldReturnErrorWhenRefreshTokenWasAlreadyConsumedOrInvalid() {
        final var sessionGateway = new FakeSessionGateway();
        final var userGateway = new FakeUserGateway();
        final var tokenProvider = new FakeTokenProvider();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, userGateway, tokenProvider, tokenHasher);

        final var result = useCase.execute(new RefreshSessionCommand(
                "old-refresh-token",
                "127.0.0.1",
                "JUnit"
        ));

        assertTrue(result.isLeft());
        assertEquals("Refresh token inválido ou expirado", firstError(result.getLeft()));
        assertEquals("hashed-old-refresh-token", sessionGateway.revokeRefreshTokenHashValue);
        assertEquals(0, userGateway.findByIdCalls);
        assertEquals(0, tokenProvider.generateCalls);
        assertEquals(0, sessionGateway.createCalls);
    }

    @Test
    @DisplayName("deve retornar erro quando usuario da sessao nao existe")
    void shouldReturnErrorWhenSessionUserDoesNotExist() {
        final var consumedSession = consumedSession(UserId.unique());
        final var sessionGateway = new FakeSessionGateway();
        sessionGateway.sessionToConsume = consumedSession;
        final var userGateway = new FakeUserGateway();
        final var tokenProvider = new FakeTokenProvider();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, userGateway, tokenProvider, tokenHasher);

        final var result = useCase.execute(new RefreshSessionCommand(
                "old-refresh-token",
                "127.0.0.1",
                "JUnit"
        ));

        assertTrue(result.isLeft());
        assertEquals("Usuário não encontrado", firstError(result.getLeft()));
        assertEquals(1, userGateway.findByIdCalls);
        assertEquals(consumedSession.getUserId(), userGateway.findByIdValue);
        assertEquals(0, tokenProvider.generateCalls);
        assertEquals(0, sessionGateway.createCalls);
    }

    private static RefreshSessionUseCase newUseCase(
            final SessionGateway sessionGateway,
            final UserGateway userGateway,
            final TokenProvider tokenProvider,
            final TokenHasher tokenHasher
    ) {
        return new RefreshSessionUseCase(
                sessionGateway,
                userGateway,
                tokenProvider,
                tokenHasher,
                new ImmediateTransactionManager(),
                900_000L,
                604_800_000L
        );
    }

    private static User validUser() {
        return User.create(
                "brunodias",
                "bruno@example.com",
                "StrongPassword123!",
                Notification.create()
        );
    }

    private static Session consumedSession(final UserId userId) {
        return Session.with(
                SessionId.unique(),
                userId,
                "hashed-old-refresh-token",
                DeviceInfo.of("127.0.0.1", "JUnit"),
                Instant.now().plusSeconds(3600),
                true,
                Instant.now(),
                Instant.now(),
                2
        );
    }

    private static final class FakeSessionGateway implements SessionGateway {
        private Session sessionToConsume;
        private String revokeRefreshTokenHashValue;
        private Instant revokeNowValue;
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
            this.revokeRefreshTokenHashValue = refreshTokenHash;
            this.revokeNowValue = now;
            return Optional.ofNullable(sessionToConsume);
        }

        @Override
        public int revokeAllByUserId(final UserId userId) {
            return 0;
        }
    }

    private static final class FakeUserGateway implements UserGateway {
        private User user;
        private int findByIdCalls;
        private UserId findByIdValue;

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
            this.findByIdCalls++;
            this.findByIdValue = id;
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<User> findByUsernameOrEmail(final String identifier) {
            return Optional.empty();
        }

        @Override
        public User update(final User user) {
            return user;
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
            return "new-access-token";
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
        @Override
        public String generate() {
            return "new-refresh-token";
        }

        @Override
        public String hash(final String token) {
            return "hashed-" + token;
        }
    }

}
