package com.btree.application.usecase.user.auth.refresh;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshSessionUseCaseTest {

    @Mock SessionGateway sessionGateway;
    @Mock UserGateway userGateway;
    @Mock TokenProvider tokenProvider;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;

    RefreshSessionUseCase useCase;

    private static final long ACCESS_TOKEN_EXPIRATION_MS  = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L;

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCase(
                sessionGateway, userGateway, tokenProvider, tokenHasher,
                transactionManager, ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUser() {
        return User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private Session buildActiveSession(UserId userId) {
        return Session.with(
                SessionId.unique(), userId, "old-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().plusSeconds(3600),
                false, Instant.now(), Instant.now(), 0
        );
    }

    private Session buildRevokedSession(UserId userId) {
        final var session = buildActiveSession(userId);
        session.revoke();
        return session;
    }

    private Session buildExpiredSession(UserId userId) {
        return Session.with(
                SessionId.unique(), userId, "old-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().minusSeconds(60),
                false, Instant.now(), Instant.now(), 0
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    void givenValidToken_whenExecute_thenReturnNewTokens() {
        final var user = buildUser();
        final var oldSession = buildActiveSession(user.getId());

        when(tokenHasher.hash("raw-old")).thenReturn("old-hash");
        when(sessionGateway.findByRefreshTokenHash("old-hash")).thenReturn(Optional.of(oldSession));
        when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenProvider.generate(any(), any(), any())).thenReturn("new-access-token");
        when(tokenHasher.generate()).thenReturn("raw-new-refresh");
        when(tokenHasher.hash("raw-new-refresh")).thenReturn("new-hash");
        when(sessionGateway.update(any())).thenReturn(oldSession);
        when(sessionGateway.create(any())).thenReturn(oldSession);

        final var command = new RefreshSessionCommand("raw-old", "127.0.0.1", "Mozilla");
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals("new-access-token", output.accessToken());
        assertEquals("raw-new-refresh", output.refreshToken());
        assertNotNull(output.accessTokenExpiresAt());
        assertEquals(user.getId().getValue().toString(), output.userId());
        assertEquals("johndoe", output.username());
        assertEquals("john@example.com", output.email());
    }

    @Test
    void givenValidToken_whenExecute_thenRevokeOldSession() {
        final var user = buildUser();
        final var oldSession = buildActiveSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(oldSession));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(tokenProvider.generate(any(), any(), any())).thenReturn("new-access");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.update(any())).thenReturn(oldSession);
        when(sessionGateway.create(any())).thenReturn(oldSession);

        useCase.execute(new RefreshSessionCommand("raw-old", null, null));

        assertTrue(oldSession.isRevoked());
        verify(sessionGateway).update(oldSession);
        verify(sessionGateway).create(any());
    }

    @Test
    void givenSessionNotFound_whenExecute_thenReturnSessionNotFoundError() {
        when(tokenHasher.hash(anyString())).thenReturn("some-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.SESSION_NOT_FOUND.message())));
    }

    @Test
    void givenRevokedSession_whenExecute_thenReturnSessionRevokedError() {
        final var user = buildUser();
        final var revokedSession = buildRevokedSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(revokedSession));

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.SESSION_REVOKED.message())));
    }

    @Test
    void givenExpiredSession_whenExecute_thenReturnSessionExpiredError() {
        final var user = buildUser();
        final var expiredSession = buildExpiredSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(expiredSession));

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.SESSION_EXPIRED.message())));
    }

    @Test
    void givenUserNotFound_whenExecute_thenReturnUserNotFoundError() {
        final var user = buildUser();
        final var session = buildActiveSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(session));
        when(userGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }
}
