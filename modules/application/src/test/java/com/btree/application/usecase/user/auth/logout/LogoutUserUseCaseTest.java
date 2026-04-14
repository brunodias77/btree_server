package com.btree.application.usecase.user.auth.logout;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LogoutUserUseCase")
class LogoutUserUseCaseTest extends UseCaseTest {

    @Test
    @DisplayName("deve retornar erro quando refresh token esta ausente")
    void shouldReturnErrorWhenRefreshTokenIsMissing() {
        final var sessionGateway = new FakeSessionGateway();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, tokenHasher);

        final var result = useCase.execute(new LogoutUserCommand(" "));

        assertTrue(result.isLeft());
        assertEquals("Refresh token inválido ou expirado", firstError(result.getLeft()));
        assertEquals(0, tokenHasher.hashCalls);
        assertEquals(0, sessionGateway.findCalls);
        assertEquals(0, sessionGateway.updateCalls);
    }

    @Test
    @DisplayName("deve ser idempotente quando sessao nao existe")
    void shouldBeIdempotentWhenSessionDoesNotExist() {
        final var sessionGateway = new FakeSessionGateway();
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, tokenHasher);

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertNull(result.get());
        assertEquals("hashed-raw-refresh-token", sessionGateway.findByRefreshTokenHashValue);
        assertEquals(0, sessionGateway.updateCalls);
    }

    @Test
    @DisplayName("deve revogar sessao ativa")
    void shouldRevokeActiveSession() {
        final var session = activeSession();
        final var sessionGateway = new FakeSessionGateway();
        sessionGateway.session = session;
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, tokenHasher);

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertTrue(session.isRevoked());
        assertEquals(1, sessionGateway.updateCalls);
        assertEquals(session, sessionGateway.updatedSession);
    }

    @Test
    @DisplayName("deve retornar sucesso sem atualizar quando sessao ja esta revogada")
    void shouldReturnSuccessWithoutUpdateWhenSessionIsAlreadyRevoked() {
        final var session = activeSession();
        session.revoke();
        final var sessionGateway = new FakeSessionGateway();
        sessionGateway.session = session;
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, tokenHasher);

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertTrue(session.isRevoked());
        assertEquals(0, sessionGateway.updateCalls);
    }

    @Test
    @DisplayName("deve retornar sucesso sem atualizar quando sessao esta expirada")
    void shouldReturnSuccessWithoutUpdateWhenSessionIsExpired() {
        final var session = expiredSession();
        final var sessionGateway = new FakeSessionGateway();
        sessionGateway.session = session;
        final var tokenHasher = new FakeTokenHasher();
        final var useCase = newUseCase(sessionGateway, tokenHasher);

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertFalse(session.isRevoked());
        assertEquals(0, sessionGateway.updateCalls);
    }

    private static LogoutUserUseCase newUseCase(
            final SessionGateway sessionGateway,
            final TokenHasher tokenHasher
    ) {
        return new LogoutUserUseCase(
                sessionGateway,
                tokenHasher,
                new ImmediateTransactionManager()
        );
    }

    private static Session activeSession() {
        return Session.with(
                SessionId.unique(),
                UserId.unique(),
                "hashed-raw-refresh-token",
                DeviceInfo.of("127.0.0.1", "JUnit"),
                Instant.now().plusSeconds(3600),
                false,
                Instant.now(),
                Instant.now(),
                0
        );
    }

    private static Session expiredSession() {
        return Session.with(
                SessionId.unique(),
                UserId.unique(),
                "hashed-raw-refresh-token",
                DeviceInfo.of("127.0.0.1", "JUnit"),
                Instant.now().minusSeconds(60),
                false,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(3600),
                0
        );
    }

    private static final class FakeSessionGateway implements SessionGateway {
        private Session session;
        private int findCalls;
        private String findByRefreshTokenHashValue;
        private int updateCalls;
        private Session updatedSession;

        @Override
        public Session create(final Session session) {
            return session;
        }

        @Override
        public Session update(final Session session) {
            this.updateCalls++;
            this.updatedSession = session;
            return session;
        }

        @Override
        public Optional<Session> findByRefreshTokenHash(final String refreshTokenHash) {
            this.findCalls++;
            this.findByRefreshTokenHashValue = refreshTokenHash;
            return Optional.ofNullable(session);
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

    private static final class FakeTokenHasher implements TokenHasher {
        private int hashCalls;

        @Override
        public String generate() {
            return "raw-refresh-token";
        }

        @Override
        public String hash(final String token) {
            this.hashCalls++;
            return "hashed-" + token;
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
