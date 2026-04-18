package com.btree.application.usecase.user.auth.verify_two_factor;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.*;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.domain.user.valueobject.DeviceInfo;
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
@DisplayName("Verify two factor use case")
class VerifyTwoFactorUseCaseTest {

    @Mock UserTokenGateway userTokenGateway;
    @Mock UserGateway userGateway;
    @Mock SessionGateway sessionGateway;
    @Mock LoginHistoryGateway loginHistoryGateway;
    @Mock TotpGateway totpGateway;
    @Mock TokenProvider tokenProvider;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;
    @Mock DomainEventPublisher eventPublisher;

    VerifyTwoFactorUseCase useCase;

    private static final long ACCESS_TOKEN_EXPIRATION_MS  = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L;

    @BeforeEach
    void setUp() {
        useCase = new VerifyTwoFactorUseCase(
                userTokenGateway, userGateway, sessionGateway, loginHistoryGateway,
                totpGateway, tokenProvider, tokenHasher, transactionManager, eventPublisher,
                ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUserWith2FA(UserId userId) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, true, "totp-secret",
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildUserWith2FAAndFailCount(UserId userId, int failCount) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, true, "totp-secret",
                false, null, failCount, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildLockedUser(UserId userId, Instant lockExpiresAt) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, true, "totp-secret",
                true, lockExpiresAt, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildDisabledUser(UserId userId) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, true, "totp-secret",
                false, null, 0, false,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildValidTwoFactorToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR.name(), "token-hash",
                Instant.now().plusSeconds(300),
                null, Instant.now()
        );
    }

    private Session buildSession(UserId userId) {
        return Session.with(
                SessionId.unique(), userId, "refresh-hash",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().plusSeconds(604_800),
                false, Instant.now(), Instant.now(), 0
        );
    }

    private void stubHappyPath(User user, UserToken token) {
        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode("totp-secret", "123456")).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);
    }

    private VerifyTwoFactorCommand buildCommand(String transactionId, String code) {
        return new VerifyTwoFactorCommand(transactionId, code, "127.0.0.1", "Mozilla");
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar tokens quando o transactionId, usuario e codigo TOTP forem validos")
    void givenValidInputs_whenExecute_thenReturnTokens() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FA(userId);
        final var token = buildValidTwoFactorToken(userId);

        stubHappyPath(user, token);

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals("access-token", output.accessToken());
        assertEquals("raw-refresh", output.refreshToken());
        assertEquals(userId.getValue().toString(), output.userId());
        assertEquals("johndoe", output.username());
        assertEquals("john@example.com", output.email());
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o transactionId nao for um UUID valido")
    void givenInvalidUuidTransactionId_whenExecute_thenReturnInvalidCredentials() {
        final var result = useCase.execute(buildCommand("not-a-valid-uuid", "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
        verifyNoInteractions(userTokenGateway);
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o token de transacao nao for encontrado")
    void givenUnknownToken_whenExecute_thenReturnInvalidCredentials() {
        when(userTokenGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(buildCommand(UserTokenId.unique().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o token nao for do tipo TWO_FACTOR")
    void givenTokenWithWrongType_whenExecute_thenReturnInvalidCredentials() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR_SETUP.name(), "token-hash",
                Instant.now().plusSeconds(300),
                null, Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o token estiver expirado")
    void givenExpiredToken_whenExecute_thenReturnInvalidCredentials() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR.name(), "token-hash",
                Instant.now().minusSeconds(1),
                null, Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o token ja tiver sido utilizado")
    void givenUsedToken_whenExecute_thenReturnInvalidCredentials() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR.name(), "token-hash",
                Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(60),
                Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve retornar credenciais invalidas quando o usuario nao for encontrado")
    void givenUserNotFound_whenExecute_thenReturnInvalidCredentials() {
        final var userId = UserId.unique();
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_CREDENTIALS.message())));
    }

    @Test
    @DisplayName("Deve retornar conta desativada quando o usuario nao estiver habilitado")
    void givenDisabledUser_whenExecute_thenReturnAccountDisabled() {
        final var userId = UserId.unique();
        final var user = buildDisabledUser(userId);
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.ACCOUNT_DISABLED.message())));
    }

    @Test
    @DisplayName("Deve retornar conta bloqueada quando o bloqueio estiver ativo")
    void givenActivelyLockedAccount_whenExecute_thenReturnAccountLocked() {
        final var userId = UserId.unique();
        final var user = buildLockedUser(userId, Instant.now().plusSeconds(900));
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.ACCOUNT_LOCKED.message())));
    }

    @Test
    @DisplayName("Deve desbloquear automaticamente e continuar o fluxo quando o prazo de bloqueio expirou")
    void givenExpiredLock_whenExecute_thenAutoUnlockAndContinue() {
        final var userId = UserId.unique();
        final var user = buildLockedUser(userId, Instant.now().minusSeconds(60));
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode("totp-secret", "123456")).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("refresh-hash");
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertFalse(user.isAccountLocked());
        assertTrue(result.isRight());
    }

    @Test
    @DisplayName("Deve retornar codigo TOTP invalido quando o codigo nao corresponder")
    void givenInvalidTotpCode_whenExecute_thenReturnInvalidTotpCode() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FA(userId);
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode("totp-secret", "000000")).thenReturn(false);

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "000000"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_TOTP_CODE.message())));
    }

    @Test
    @DisplayName("Deve incrementar o contador de falhas quando o codigo TOTP for invalido")
    void givenInvalidTotpCode_whenExecute_thenIncrementFailCount() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FA(userId);
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode(any(), any())).thenReturn(false);

        useCase.execute(buildCommand(token.getId().getValue().toString(), "000000"));

        assertEquals(1, user.getAccessFailedCount());
    }

    @Test
    @DisplayName("Deve bloquear a conta quando o limite de tentativas invalidas for atingido")
    void givenMaxFailedAttempts_whenInvalidCode_thenLockAccount() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FAAndFailCount(userId,
                com.btree.application.usecase.user.auth.login.LoginUserUseCase.MAX_FAILED_ATTEMPTS - 1);
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode(any(), any())).thenReturn(false);

        useCase.execute(buildCommand(token.getId().getValue().toString(), "000000"));

        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockExpiresAt());
    }

    @Test
    @DisplayName("Deve marcar o token como usado apos verificar o codigo com sucesso")
    void givenSuccessfulVerify_whenExecute_thenMarkTokenAsUsed() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FA(userId);
        final var token = buildValidTwoFactorToken(userId);

        stubHappyPath(user, token);

        useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Deve zerar o contador de falhas quando o codigo for verificado com sucesso")
    void givenPositiveFailCount_whenVerifySucceeds_thenResetFailCount() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FAAndFailCount(userId, 3);
        final var token = buildValidTwoFactorToken(userId);

        stubHappyPath(user, token);

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isRight());
        assertEquals(0, user.getAccessFailedCount());
    }

    @Test
    @DisplayName("Deve retornar left quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnLeft() {
        final var userId = UserId.unique();
        final var user = buildUserWith2FA(userId);
        final var token = buildValidTwoFactorToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.isValidCode(any(), any())).thenReturn(true);
        when(tokenProvider.generate(any(), any(), any())).thenReturn("access-token");
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash(any())).thenReturn("refresh-hash");
        doThrow(new RuntimeException("DB error")).when(transactionManager).execute(any());

        final var result = useCase.execute(buildCommand(token.getId().getValue().toString(), "123456"));

        assertTrue(result.isLeft());
    }
}
