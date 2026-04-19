package com.btree.application.usecase.user.auth.enable_two_factor;

import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.TotpGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.StringEncryptor;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Enable two factor use case")
class EnableTwoFactorUseCaseTest {

    @Mock UserGateway userGateway;
    @Mock UserTokenGateway userTokenGateway;
    @Mock TotpGateway totpGateway;
    @Mock StringEncryptor stringEncryptor;
    @Mock TransactionManager transactionManager;
    @Mock DomainEventPublisher eventPublisher;

    EnableTwoFactorUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EnableTwoFactorUseCase(
                userGateway, userTokenGateway, totpGateway,
                stringEncryptor, transactionManager, eventPublisher
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUserWithout2FA(UserId userId) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildValidSetupToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR_SETUP.name(), "encrypted-secret",
                Instant.now().plusSeconds(900),
                null, Instant.now()
        );
    }

    private EnableTwoFactorCommand buildCommand(UserId userId, UserTokenId tokenId, String code) {
        return new EnableTwoFactorCommand(
                userId.getValue().toString(),
                tokenId.getValue().toString(),
                code
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve habilitar 2FA quando o token e o codigo TOTP forem validos")
    void givenValidTokenAndCode_whenExecute_thenEnable2FA() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt("encrypted-secret")).thenReturn("RAW-SECRET");
        when(totpGateway.isValidCode("RAW-SECRET", "123456")).thenReturn(true);
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isRight());
        assertTrue(user.isTwoFactorEnabled());
        assertEquals("RAW-SECRET", user.getTwoFactorSecret());
    }

    @Test
    @DisplayName("Deve retornar erro quando o token de setup nao for encontrado")
    void givenUnknownToken_whenExecute_thenReturnTokenNotFound() {
        when(userTokenGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(new EnableTwoFactorCommand(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "123456"
        ));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o tipo de token for invalido")
    void givenWrongTokenType_whenExecute_thenReturnInvalidTokenType() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.PASSWORD_RESET.name(), "encrypted-secret",
                Instant.now().plusSeconds(900),
                null, Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_INVALID_TYPE.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token pertencer a outro usuario")
    void givenTokenFromDifferentUser_whenExecute_thenReturnTokenNotFound() {
        final var userId = UserId.unique();
        final var otherUserId = UserId.unique();
        final var token = buildValidSetupToken(otherUserId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token estiver expirado")
    void givenExpiredToken_whenExecute_thenReturnTokenExpired() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR_SETUP.name(), "encrypted-secret",
                Instant.now().minusSeconds(1),
                null, Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_EXPIRED.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token ja tiver sido utilizado")
    void givenAlreadyUsedToken_whenExecute_thenReturnTokenAlreadyUsed() {
        final var userId = UserId.unique();
        final var token = UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR_SETUP.name(), "encrypted-secret",
                Instant.now().plusSeconds(900),
                Instant.now().minusSeconds(60),
                Instant.now()
        );

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_ALREADY_USED.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o usuario nao for encontrado")
    void givenUserNotFound_whenExecute_thenReturnUserNotFound() {
        final var userId = UserId.unique();
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o codigo TOTP for invalido")
    void givenInvalidTotpCode_whenExecute_thenReturnInvalidTotpCode() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt("encrypted-secret")).thenReturn("RAW-SECRET");
        when(totpGateway.isValidCode("RAW-SECRET", "000000")).thenReturn(false);

        final var result = useCase.execute(buildCommand(userId, token.getId(), "000000"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.INVALID_TOTP_CODE.message())));
    }

    @Test
    @DisplayName("Deve marcar o token como usado apos habilitar o 2FA")
    void givenSuccessfulEnable_whenExecute_thenMarkTokenAsUsed() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt(any())).thenReturn("RAW-SECRET");
        when(totpGateway.isValidCode(any(), any())).thenReturn(true);
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);

        useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Deve publicar domain event de 2FA habilitado apos sucesso")
    void givenSuccessfulEnable_whenExecute_thenPublishTwoFactorEnabledEvent() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt(any())).thenReturn("RAW-SECRET");
        when(totpGateway.isValidCode(any(), any())).thenReturn(true);
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);

        useCase.execute(buildCommand(userId, token.getId(), "123456"));

        verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
    }

    @Test
    @DisplayName("Deve descriptografar o secret do token para validar o codigo TOTP")
    void givenValidToken_whenExecute_thenDecryptTokenHashToValidateCode() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt("encrypted-secret")).thenReturn("DECRYPTED-SECRET");
        when(totpGateway.isValidCode("DECRYPTED-SECRET", "123456")).thenReturn(true);
        when(userGateway.update(any())).thenReturn(user);
        when(userTokenGateway.update(any())).thenReturn(token);

        useCase.execute(buildCommand(userId, token.getId(), "123456"));

        verify(stringEncryptor).decrypt("encrypted-secret");
        verify(totpGateway).isValidCode("DECRYPTED-SECRET", "123456");
    }

    @Test
    @DisplayName("Deve retornar left quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnLeft() {
        final var userId = UserId.unique();
        final var user = buildUserWithout2FA(userId);
        final var token = buildValidSetupToken(userId);

        when(userTokenGateway.findById(any())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(stringEncryptor.decrypt(any())).thenReturn("RAW-SECRET");
        when(totpGateway.isValidCode(any(), any())).thenReturn(true);
        doThrow(new RuntimeException("DB error")).when(transactionManager).execute(any());

        final var result = useCase.execute(buildCommand(userId, token.getId(), "123456"));

        assertTrue(result.isLeft());
    }
}
