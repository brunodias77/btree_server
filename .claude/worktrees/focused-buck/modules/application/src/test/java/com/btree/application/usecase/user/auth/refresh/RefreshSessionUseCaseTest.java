package com.btree.application.usecase.user.auth.refresh;

import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionCommand;
import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionUseCase;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.error.AuthError;
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
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("Refresh session use case")
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
    @DisplayName("Deve retornar novos tokens quando o refresh token for valido")
    void givenValidToken_whenExecute_thenReturnNewTokens() {
        final var user = buildUser();
        final var oldSession = buildActiveSession(user.getId());

        when(tokenHasher.hash("raw-old")).thenReturn("old-hash");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class))).thenReturn(Optional.of(oldSession));
        when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenProvider.generate(any(), any(), any())).thenReturn("new-access-token");
        when(tokenHasher.generate()).thenReturn("raw-new-refresh");
        when(tokenHasher.hash("raw-new-refresh")).thenReturn("new-hash");
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
    @DisplayName("Deve revogar sessao antiga quando o refresh token for valido")
    void givenValidToken_whenExecute_thenRevokeOldSession() {
        final var user = buildUser();
        final var oldSession = buildActiveSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class))).thenReturn(Optional.of(oldSession));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(tokenProvider.generate(any(), any(), any())).thenReturn("new-access");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.create(any())).thenReturn(oldSession);

        useCase.execute(new RefreshSessionCommand("raw-old", null, null));

        verify(sessionGateway).revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class));
        verify(sessionGateway).create(any());
    }

    @Test
    @DisplayName("Deve retornar refresh token invalido quando a sessao nao for encontrada")
    void givenSessionNotFound_whenExecute_thenReturnSessionNotFoundError() {
        when(tokenHasher.hash(anyString())).thenReturn("some-hash");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("some-hash"), any(Instant.class))).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(AuthError.INVALID_REFRESH_TOKEN.message())));
    }

    @Test
    @DisplayName("Deve retornar refresh token invalido quando a sessao estiver revogada")
    void givenRevokedSession_whenExecute_thenReturnSessionRevokedError() {
        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class))).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(AuthError.INVALID_REFRESH_TOKEN.message())));
    }

    @Test
    @DisplayName("Deve retornar refresh token invalido quando a sessao estiver expirada")
    void givenExpiredSession_whenExecute_thenReturnSessionExpiredError() {
        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class))).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(AuthError.INVALID_REFRESH_TOKEN.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o usuario nao for encontrado")
    void givenUserNotFound_whenExecute_thenReturnUserNotFoundError() {
        final var user = buildUser();
        final var session = buildActiveSession(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("old-hash");
        when(tokenHasher.generate()).thenReturn("raw-new");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("old-hash"), any(Instant.class))).thenReturn(Optional.of(session));
        when(userGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("raw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }
}
