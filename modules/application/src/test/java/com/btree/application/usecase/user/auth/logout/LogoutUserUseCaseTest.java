package com.btree.application.usecase.user.auth.logout;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LogoutUserUseCase")
@ExtendWith(MockitoExtension.class)
class LogoutUserUseCaseTest extends UseCaseTest {

    @Mock private SessionGateway sessionGateway;
    @Mock private TokenHasher tokenHasher;

    private LogoutUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUserUseCase(
                sessionGateway,
                tokenHasher,
                new ImmediateTransactionManager()
        );
    }

    @Test
    @DisplayName("deve retornar erro quando refresh token esta ausente")
    void shouldReturnErrorWhenRefreshTokenIsMissing() {
        final var result = useCase.execute(new LogoutUserCommand(" "));

        assertTrue(result.isLeft());
        assertEquals("Refresh token inválido ou expirado", firstError(result.getLeft()));
        verify(tokenHasher, never()).hash(any());
        verify(sessionGateway, never()).findByRefreshTokenHash(any());
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve ser idempotente quando sessao nao existe")
    void shouldBeIdempotentWhenSessionDoesNotExist() {
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-raw-refresh-token");
        when(sessionGateway.findByRefreshTokenHash("hashed-raw-refresh-token")).thenReturn(Optional.empty());

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertNull(result.get());
        verify(sessionGateway).findByRefreshTokenHash("hashed-raw-refresh-token");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve revogar sessao ativa")
    void shouldRevokeActiveSession() {
        final var session = activeSession();
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-raw-refresh-token");
        when(sessionGateway.findByRefreshTokenHash("hashed-raw-refresh-token")).thenReturn(Optional.of(session));
        when(sessionGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertTrue(session.isRevoked());
        verify(sessionGateway).update(session);
    }

    @Test
    @DisplayName("deve retornar sucesso sem atualizar quando sessao ja esta revogada")
    void shouldReturnSuccessWithoutUpdateWhenSessionIsAlreadyRevoked() {
        final var session = activeSession();
        session.revoke();
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-raw-refresh-token");
        when(sessionGateway.findByRefreshTokenHash("hashed-raw-refresh-token")).thenReturn(Optional.of(session));

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertTrue(session.isRevoked());
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve retornar sucesso sem atualizar quando sessao esta expirada")
    void shouldReturnSuccessWithoutUpdateWhenSessionIsExpired() {
        final var session = expiredSession();
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-raw-refresh-token");
        when(sessionGateway.findByRefreshTokenHash("hashed-raw-refresh-token")).thenReturn(Optional.of(session));

        final var result = useCase.execute(new LogoutUserCommand("raw-refresh-token"));

        assertTrue(result.isRight());
        assertFalse(session.isRevoked());
        verify(sessionGateway, never()).update(any());
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
}
