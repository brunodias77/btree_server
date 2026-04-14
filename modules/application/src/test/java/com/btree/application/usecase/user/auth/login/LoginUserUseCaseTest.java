package com.btree.application.usecase.user.auth.login;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LoginUserUseCase")
@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest extends UseCaseTest {

    @Mock private UserGateway userGateway;
    @Mock private SessionGateway sessionGateway;
    @Mock private UserTokenGateway userTokenGateway;
    @Mock private LoginHistoryGateway loginHistoryGateway;
    @Mock private PasswordHasher passwordHasher;
    @Mock private TokenProvider tokenProvider;
    @Mock private TokenHasher tokenHasher;
    @Mock private DomainEventPublisher domainEventPublisher;

    private LoginUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUserUseCase(
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

    @Test
    @DisplayName("deve autenticar usuario e criar sessao")
    void shouldAuthenticateUserAndCreateSession() {
        final var user = validUser();
        when(userGateway.findByUsernameOrEmail("bruno@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), eq("HashedPassword123!"))).thenReturn(true);
        when(tokenProvider.generate(
                eq(user.getId().getValue().toString()),
                eq(Map.of("username", "brunodias", "email", "bruno@example.com")),
                any()
        )).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh-token");
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-raw-refresh-token");
        when(sessionGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loginHistoryGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(command("BRUNO@EXAMPLE.COM", "StrongPassword123!"));

        assertTrue(result.isRight());
        assertEquals("access-token", result.get().accessToken());
        assertEquals("raw-refresh-token", result.get().refreshToken());
        assertEquals(user.getId().getValue().toString(), result.get().userId());
        assertEquals("brunodias", result.get().username());
        assertEquals("bruno@example.com", result.get().email());
        assertFalse(result.get().requiresTwoFactor());
        assertNull(result.get().transactionId());

        verify(userGateway).findByUsernameOrEmail("bruno@example.com");
        verify(passwordHasher).matches(any(), eq("HashedPassword123!"));
        verify(tokenProvider).generate(any(), any(), any());
        verify(tokenHasher).generate();
        verify(tokenHasher).hash("raw-refresh-token");
        verify(sessionGateway).create(argThat(s ->
                user.getId().equals(s.getUserId()) &&
                "hashed-raw-refresh-token".equals(s.getRefreshTokenHash())
        ));
        verify(loginHistoryGateway).create(argThat(h -> h.isSuccess()));
        verify(userGateway).update(any());
        verify(userTokenGateway, never()).create(any());
    }

    @Test
    @DisplayName("deve registrar falha quando senha esta incorreta")
    void shouldRecordFailureWhenPasswordIsInvalid() {
        final var user = validUser();
        when(userGateway.findByUsernameOrEmail("brunodias")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(eq("wrong-password"), eq("HashedPassword123!"))).thenReturn(false);
        when(userGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loginHistoryGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(command("brunodias", "wrong-password"));

        assertTrue(result.isLeft());
        assertEquals("Credenciais inválidas", firstError(result.getLeft()));
        assertEquals(1, user.getAccessFailedCount());
        verify(userGateway).update(any());
        verify(loginHistoryGateway).create(argThat(h ->
                !h.isSuccess() && "Senha inválida".equals(h.getFailureReason())
        ));
        verify(sessionGateway, never()).create(any());
    }

    @Test
    @DisplayName("deve retornar erro sem side effects quando usuario nao existe")
    void shouldReturnLeftWithoutSideEffectsWhenUserDoesNotExist() {
        when(userGateway.findByUsernameOrEmail("missing@example.com")).thenReturn(Optional.empty());

        final var result = useCase.execute(command("missing@example.com", "StrongPassword123!"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().isEmpty());
        verify(userGateway).findByUsernameOrEmail("missing@example.com");
        verify(passwordHasher, never()).matches(any(), any());
        verify(tokenProvider, never()).generate(any(), any(), any());
        verify(tokenHasher, never()).generate();
        verify(sessionGateway, never()).create(any());
        verify(userTokenGateway, never()).create(any());
        verify(loginHistoryGateway, never()).create(any());
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve exigir 2FA quando usuario possui dois fatores ativo")
    void shouldRequireTwoFactorWhenEnabled() {
        final var user = validUser();
        user.enableTwoFactor("totp-secret");
        when(userGateway.findByUsernameOrEmail("brunodias")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), eq("HashedPassword123!"))).thenReturn(true);
        when(tokenHasher.generate()).thenReturn("raw-2fa-token");
        when(tokenHasher.hash("raw-2fa-token")).thenReturn("hashed-raw-2fa-token");
        when(userTokenGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(command("brunodias", "StrongPassword123!"));

        assertTrue(result.isRight());
        assertTrue(result.get().requiresTwoFactor());
        assertNull(result.get().accessToken());
        assertNull(result.get().refreshToken());
        assertNull(result.get().accessTokenExpiresAt());
        assertNotNull(result.get().transactionId());
        assertEquals(user.getId().getValue().toString(), result.get().userId());

        verify(userTokenGateway).create(argThat((UserToken t) ->
                TokenType.TWO_FACTOR.name().equals(t.getTokenType()) &&
                user.getId().equals(t.getUserId())
        ));
        verify(userGateway).update(any());
        verify(tokenProvider, never()).generate(any(), any(), any());
        verify(sessionGateway, never()).create(any());
        verify(loginHistoryGateway, never()).create(any());
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
}
