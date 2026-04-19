package com.btree.application.usecase.user.auth.confirm_password_reset;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Confirm password reset use case")
class ConfirmPasswordResetUseCaseTest extends UseCaseTest {

    @Mock UserTokenGateway userTokenGateway;
    @Mock UserGateway userGateway;
    @Mock TokenHasher tokenHasher;
    @Mock PasswordHasher passwordHasher;
    @Mock DomainEventPublisher eventPublisher;
    @Mock TransactionManager transactionManager;

    ConfirmPasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmPasswordResetUseCase(
                userTokenGateway, userGateway, tokenHasher,
                passwordHasher, eventPublisher, transactionManager
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildEnabledUser() {
        return User.with(
                UserId.unique(), "joao_silva", "joao@exemplo.com",
                true, "old-hash",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildValidToken(final UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.PASSWORD_RESET.name(), "token-hash",
                Instant.now().plusSeconds(1800),
                null,
                Instant.now()
        );
    }

    private UserToken buildExpiredToken(final UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.PASSWORD_RESET.name(), "token-hash",
                Instant.now().minusSeconds(60),
                null,
                Instant.now()
        );
    }

    private UserToken buildUsedToken(final UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.PASSWORD_RESET.name(), "token-hash",
                Instant.now().plusSeconds(1800),
                Instant.now().minusSeconds(10),
                Instant.now()
        );
    }

    private UserToken buildWrongTypeToken(final UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.EMAIL_VERIFICATION.name(), "token-hash",
                Instant.now().plusSeconds(1800),
                null,
                Instant.now()
        );
    }

    private void stubHappyPath(final User user, final UserToken token) {
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));
        when(passwordHasher.hash("NovaSenha@1234")).thenReturn("new-hashed-pw");
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve redefinir senha com sucesso quando o comando for valido")
    void givenValidCommand_whenExecute_thenResetPasswordSuccessfully() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());
        stubHappyPath(user, token);

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isRight());
        assertNull(result.get());
        verify(userGateway).update(user);
        verify(userTokenGateway).update(token);
        verify(eventPublisher).publishAll(any());
    }

    @Test
    @DisplayName("Deve atualizar a senha do usuario com o hash da nova senha")
    void givenValidCommand_whenExecute_thenUserPasswordIsUpdated() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());
        stubHappyPath(user, token);

        useCase.execute(new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234"));

        assertEquals("new-hashed-pw", user.getPasswordHash());
        verify(passwordHasher).hash("NovaSenha@1234");
    }

    @Test
    @DisplayName("Deve marcar o token como utilizado apos redefinir a senha")
    void givenValidCommand_whenExecute_thenTokenIsMarkedAsUsed() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());
        stubHappyPath(user, token);

        useCase.execute(new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234"));

        assertTrue(token.isUsed());
        verify(userTokenGateway).update(token);
    }

    @Test
    @DisplayName("Deve retornar erro quando o token nao for encontrado")
    void givenNonExistentToken_whenExecute_thenReturnTokenNotFoundError() {
        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.empty());

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.TOKEN_NOT_FOUND.message()));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando o token for de tipo incorreto")
    void givenWrongTypeToken_whenExecute_thenReturnTokenInvalidTypeError() {
        final var userId = UserId.unique();
        final var token = buildWrongTypeToken(userId);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.TOKEN_INVALID_TYPE.message()));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando o token estiver expirado")
    void givenExpiredToken_whenExecute_thenReturnTokenExpiredError() {
        final var userId = UserId.unique();
        final var token = buildExpiredToken(userId);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.TOKEN_EXPIRED.message()));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando o token ja tiver sido utilizado")
    void givenUsedToken_whenExecute_thenReturnTokenAlreadyUsedError() {
        final var userId = UserId.unique();
        final var token = buildUsedToken(userId);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.TOKEN_ALREADY_USED.message()));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando o usuario nao for encontrado")
    void givenValidTokenButUserNotFound_whenExecute_thenReturnUserNotFoundError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.empty());

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.USER_NOT_FOUND.message()));
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando a nova senha for nula")
    void givenNullPassword_whenExecute_thenReturnPasswordValidationError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", null)
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando a nova senha for muito curta")
    void givenTooShortPassword_whenExecute_thenReturnPasswordValidationError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "Ab1")
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando a nova senha nao tiver letra maiuscula")
    void givenPasswordWithoutUppercase_whenExecute_thenReturnPasswordValidationError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "semaiuscula1")
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando a nova senha nao tiver letra minuscula")
    void givenPasswordWithoutLowercase_whenExecute_thenReturnPasswordValidationError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "SEMMINUSCULA1")
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando a nova senha nao tiver digito numerico")
    void givenPasswordWithoutDigit_whenExecute_thenReturnPasswordValidationError() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "SemDigitoNaSenha")
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("Deve retornar Left quando ocorrer excecao na transacao")
    void givenTransactionException_whenExecute_thenReturnLeft() {
        final var user = buildEnabledUser();
        final var token = buildValidToken(user.getId());

        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(token.getUserId())).thenReturn(Optional.of(user));
        when(passwordHasher.hash(anyString())).thenReturn("new-hashed-pw");
        doThrow(new RuntimeException("DB failure")).when(transactionManager).execute(any());

        final var result = useCase.execute(
                new ConfirmPasswordResetCommand("raw-token", "NovaSenha@1234")
        );

        assertTrue(result.isLeft());
    }
}
