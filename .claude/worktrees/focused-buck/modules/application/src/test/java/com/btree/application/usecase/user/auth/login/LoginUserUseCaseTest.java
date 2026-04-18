package com.btree.application.usecase.user.auth.login;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Login user use case")
class LoginUserUseCaseTest {

    @Mock UserGateway userGateway;
    @Mock SessionGateway sessionGateway;
    @Mock UserTokenGateway userTokenGateway;
    @Mock LoginHistoryGateway loginHistoryGateway;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenProvider tokenProvider;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;
    @Mock DomainEventPublisher eventPublisher;

    LoginUserUseCase useCase;

    private static final long ACCESS_TOKEN_EXPIRATION_MS  = 900_000L;  // 15 min
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L; // 7 days

    @BeforeEach
    void setUp() {
        useCase = new LoginUserUseCase(
                userGateway, sessionGateway, userTokenGateway, loginHistoryGateway,
                passwordHasher, tokenProvider, tokenHasher,
                transactionManager, eventPublisher,
                ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS
        );
        // Por padrão, o TransactionManager executa o supplier imediatamente
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildActiveUser() {
        return User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private Session buildSession(UserId userId) {
        return Session.with(
                SessionId.unique(), userId, "refresh-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().plusSeconds(3600),
                false, Instant.now(), Instant.now(), 0
        );
    }

    private UserToken buildTwoFactorToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR.name(), "token-hash",
                Instant.now().plusSeconds(300),
                null, Instant.now()
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar tokens quando as credenciais forem validas")
    void givenValidCredentials_whenExecute_thenReturnTokens() {
        final var user = buildActiveUser();
        final var session = buildSession(user.getId());

        when(userGateway.findByUsernameOrEmail("johndoe")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret", user.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(sessionGateway.create(any())).thenReturn(session);
        when(loginHistoryGateway.create(any())).thenReturn(null);

        final var command = new LoginUserCommand("johndoe", "secret", "127.0.0.1", "Mozilla");
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals("access-token", output.accessToken());
        assertEquals("raw-refresh", output.refreshToken());
        assertFalse(output.requiresTwoFactor());
        assertNull(output.transactionId());
        assertEquals(user.getId().getValue().toString(), output.userId());
    }

    @Test
    @DisplayName("Deve retornar left sem erros quando o identificador nao existir")
    void givenUnknownIdentifier_whenExecute_thenReturnLeftWithNoErrors() {
        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(new LoginUserCommand("nobody", "pw", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().isEmpty());
    }

    @Test
    @DisplayName("Deve retornar left quando o usuario estiver desabilitado")
    void givenDisabledUser_whenExecute_thenReturnLeft() {
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                false, null, 0, false, // enabled = false
                Instant.now(), Instant.now(), 0, null, null
        );
        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));

        final var result = useCase.execute(new LoginUserCommand("johndoe", "secret", null, null));

        assertTrue(result.isLeft());
    }

    @Test
    @DisplayName("Deve retornar erro de conta bloqueada quando o bloqueio estiver ativo")
    void givenLockedAccount_whenExecute_thenReturnAccountLockedError() {
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                true, Instant.now().plusSeconds(900), // locked, expires in future
                0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));

        final var result = useCase.execute(new LoginUserCommand("johndoe", "secret", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.ACCOUNT_LOCKED.message())));
    }

    @Test
    @DisplayName("Deve desbloquear automaticamente e fazer login quando o bloqueio expirou")
    void givenExpiredLock_whenExecute_thenAutoUnlockAndLogin() {
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                true, Instant.now().minusSeconds(60), // lock already expired
                0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
        final var session = buildSession(user.getId());

        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash(any())).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(sessionGateway.create(any())).thenReturn(session);
        when(loginHistoryGateway.create(any())).thenReturn(null);

        final var result = useCase.execute(new LoginUserCommand("johndoe", "secret", null, null));

        assertTrue(result.isRight());
        assertFalse(user.isAccountLocked());
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando a senha estiver incorreta")
    void givenInvalidPassword_whenExecute_thenReturnInvalidCredentials() {
        final var user = buildActiveUser();
        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        final var result = useCase.execute(new LoginUserCommand("johndoe", "wrong", null, null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve bloquear a conta quando exceder o limite de tentativas falhas")
    void givenRepeatedFailedAttempts_whenExceedsMax_thenLockAccount() {
        // accessFailedCount starts at MAX_FAILED_ATTEMPTS - 1, so next failure triggers lock
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                false, null,
                LoginUserUseCase.MAX_FAILED_ATTEMPTS - 1, // one failure away from lock
                true, Instant.now(), Instant.now(), 0, null, null
        );
        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        useCase.execute(new LoginUserCommand("johndoe", "wrong", null, null));

        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockExpiresAt());
    }

    @Test
    @DisplayName("Deve zerar contador de falhas quando o login for bem-sucedido")
    void givenPositiveFailCount_whenLoginSucceeds_thenResetFailCount() {
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, false, null,
                false, null, 3, true, // 3 prior failures
                Instant.now(), Instant.now(), 0, null, null
        );
        final var session = buildSession(user.getId());

        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash(any())).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(sessionGateway.create(any())).thenReturn(session);
        when(loginHistoryGateway.create(any())).thenReturn(null);

        final var result = useCase.execute(new LoginUserCommand("johndoe", "secret", null, null));

        assertTrue(result.isRight());
        assertEquals(0, user.getAccessFailedCount());
    }

    @Test
    @DisplayName("Deve exigir segundo fator quando 2FA estiver habilitado")
    void givenTwoFactorEnabled_whenExecute_thenReturnTwoFactorRequired() {
        final var user = User.with(
                UserId.unique(), "johndoe", "john@example.com",
                false, "hashed-pw",
                null, false, true, "totp-secret", // 2FA enabled
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
        final var twoFactorToken = buildTwoFactorToken(user.getId());

        when(userGateway.findByUsernameOrEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenHasher.hash(any())).thenReturn("token-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.create(any())).thenReturn(twoFactorToken);

        final var result = useCase.execute(new LoginUserCommand("johndoe", "secret", null, null));

        assertTrue(result.isRight());
        final var output = result.get();
        assertTrue(output.requiresTwoFactor());
        assertNotNull(output.transactionId());
        assertNull(output.accessToken());
        assertNull(output.refreshToken());
    }

    @Test
    @DisplayName("Deve normalizar email para minusculo antes de buscar o usuario")
    void givenEmailAsIdentifier_whenExecute_thenFindByLowercaseIdentifier() {
        final var user = buildActiveUser();
        final var session = buildSession(user.getId());

        when(userGateway.findByUsernameOrEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash(any())).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(sessionGateway.create(any())).thenReturn(session);
        when(loginHistoryGateway.create(any())).thenReturn(null);

        final var result = useCase.execute(new LoginUserCommand("JOHN@EXAMPLE.COM", "secret", null, null));

        assertTrue(result.isRight());
        verify(userGateway).findByUsernameOrEmail("john@example.com");
    }
}
