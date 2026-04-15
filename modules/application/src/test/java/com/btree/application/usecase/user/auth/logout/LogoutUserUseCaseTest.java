package com.btree.application.usecase.user.auth.logout;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.error.AuthError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutUserUseCaseTest {

    @Mock SessionGateway sessionGateway;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;

    LogoutUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUserUseCase(sessionGateway, tokenHasher, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Session buildActiveSession() {
        return Session.with(
                SessionId.unique(), UserId.unique(), "refresh-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().plusSeconds(3600),
                false, Instant.now(), Instant.now(), 0
        );
    }

    private Session buildRevokedSession() {
        final var session = buildActiveSession();
        session.revoke();
        return session;
    }

    private Session buildExpiredSession() {
        return Session.with(
                SessionId.unique(), UserId.unique(), "refresh-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().minusSeconds(60), // expired
                false, Instant.now(), Instant.now(), 0
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    void givenActiveSession_whenExecute_thenRevokeSession() {
        final var session = buildActiveSession();
        when(tokenHasher.hash("raw-token")).thenReturn("refresh-hash");
        when(sessionGateway.findByRefreshTokenHash("refresh-hash")).thenReturn(Optional.of(session));
        when(sessionGateway.update(any())).thenReturn(session);

        final var result = useCase.execute(new LogoutUserCommand("raw-token"));

        assertTrue(result.isRight());
        assertNull(result.get());
        assertTrue(session.isRevoked());
        verify(sessionGateway).update(session);
    }

    @Test
    void givenNullRefreshToken_whenExecute_thenReturnLeft() {
        final var result = useCase.execute(new LogoutUserCommand(null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(AuthError.INVALID_REFRESH_TOKEN.message())));
        verify(tokenHasher, never()).hash(anyString());
    }

    @Test
    void givenBlankRefreshToken_whenExecute_thenReturnLeft() {
        final var result = useCase.execute(new LogoutUserCommand("   "));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(AuthError.INVALID_REFRESH_TOKEN.message())));
        verify(tokenHasher, never()).hash(anyString());
    }

    @Test
    void givenSessionNotFound_whenExecute_thenReturnRightIdempotently() {
        when(tokenHasher.hash(anyString())).thenReturn("some-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.empty());

        final var result = useCase.execute(new LogoutUserCommand("raw-token"));

        assertTrue(result.isRight());
        verify(sessionGateway, never()).update(any());
    }

    @Test
    void givenRevokedSession_whenExecute_thenReturnRightWithoutUpdating() {
        final var session = buildRevokedSession();
        when(tokenHasher.hash(anyString())).thenReturn("refresh-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(session));

        final var result = useCase.execute(new LogoutUserCommand("raw-token"));

        assertTrue(result.isRight());
        verify(sessionGateway, never()).update(any());
    }

    @Test
    void givenExpiredSession_whenExecute_thenReturnRightWithoutUpdating() {
        final var session = buildExpiredSession();
        when(tokenHasher.hash(anyString())).thenReturn("refresh-hash");
        when(sessionGateway.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(session));

        final var result = useCase.execute(new LogoutUserCommand("raw-token"));

        assertTrue(result.isRight());
        verify(sessionGateway, never()).update(any());
    }
}
