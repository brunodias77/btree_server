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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RefreshSessionUseCase")
@ExtendWith(MockitoExtension.class)
class RefreshSessionUseCaseTest extends UseCaseTest {

    @Mock private SessionGateway sessionGateway;
    @Mock private UserGateway userGateway;
    @Mock private TokenProvider tokenProvider;
    @Mock private TokenHasher tokenHasher;

    private RefreshSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCase(
                sessionGateway,
                userGateway,
                tokenProvider,
                tokenHasher,
                new ImmediateTransactionManager(),
                900_000L,
                604_800_000L
        );
    }

    @Test
    @DisplayName("deve rotacionar refresh token e criar nova sessao")
    void shouldRotateRefreshTokenAndCreateNewSession() {
        final var user = validUser();
        final var consumed = consumedSession(user.getId());
        when(tokenHasher.hash("old-refresh-token")).thenReturn("hashed-old-refresh-token");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("hashed-old-refresh-token"), any()))
                .thenReturn(Optional.of(consumed));
        when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenProvider.generate(
                eq(user.getId().getValue().toString()),
                eq(Map.of("username", "brunodias", "email", "bruno@example.com")),
                any()
        )).thenReturn("new-access-token");
        when(tokenHasher.generate()).thenReturn("new-refresh-token");
        when(tokenHasher.hash("new-refresh-token")).thenReturn("hashed-new-refresh-token");
        when(sessionGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(new RefreshSessionCommand("old-refresh-token", "127.0.0.1", "JUnit"));

        assertTrue(result.isRight());
        assertEquals("new-access-token", result.get().accessToken());
        assertEquals("new-refresh-token", result.get().refreshToken());
        assertEquals(user.getId().getValue().toString(), result.get().userId());
        assertEquals("brunodias", result.get().username());
        assertEquals("bruno@example.com", result.get().email());
        assertNotNull(result.get().accessTokenExpiresAt());

        verify(sessionGateway).revokeActiveByRefreshTokenHash(eq("hashed-old-refresh-token"), any());
        verify(userGateway).findById(user.getId());
        verify(tokenProvider).generate(any(), any(), any());
        verify(sessionGateway).create(argThat(s ->
                user.getId().equals(s.getUserId()) &&
                "hashed-new-refresh-token".equals(s.getRefreshTokenHash()) &&
                consumed.getDeviceInfo().equals(s.getDeviceInfo())
        ));
    }

    @Test
    @DisplayName("deve retornar erro quando refresh token ja foi consumido ou esta invalido")
    void shouldReturnErrorWhenRefreshTokenWasAlreadyConsumedOrInvalid() {
        when(tokenHasher.hash("old-refresh-token")).thenReturn("hashed-old-refresh-token");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("hashed-old-refresh-token"), any()))
                .thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("old-refresh-token", "127.0.0.1", "JUnit"));

        assertTrue(result.isLeft());
        assertEquals("Refresh token inválido ou expirado", firstError(result.getLeft()));
        verify(sessionGateway).revokeActiveByRefreshTokenHash(eq("hashed-old-refresh-token"), any());
        verify(userGateway, never()).findById(any());
        verify(tokenProvider, never()).generate(any(), any(), any());
        verify(sessionGateway, never()).create(any());
    }

    @Test
    @DisplayName("deve retornar erro quando usuario da sessao nao existe")
    void shouldReturnErrorWhenSessionUserDoesNotExist() {
        final var orphanUserId = UserId.unique();
        final var consumed = consumedSession(orphanUserId);
        when(tokenHasher.hash("old-refresh-token")).thenReturn("hashed-old-refresh-token");
        when(sessionGateway.revokeActiveByRefreshTokenHash(eq("hashed-old-refresh-token"), any()))
                .thenReturn(Optional.of(consumed));
        when(userGateway.findById(orphanUserId)).thenReturn(Optional.empty());

        final var result = useCase.execute(new RefreshSessionCommand("old-refresh-token", "127.0.0.1", "JUnit"));

        assertTrue(result.isLeft());
        assertEquals("Usuário não encontrado", firstError(result.getLeft()));
        verify(userGateway).findById(orphanUserId);
        verify(tokenProvider, never()).generate(any(), any(), any());
        verify(sessionGateway, never()).create(any());
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
}
