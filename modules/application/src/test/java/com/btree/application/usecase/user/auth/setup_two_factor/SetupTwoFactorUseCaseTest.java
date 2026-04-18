package com.btree.application.usecase.user.auth.setup_two_factor;

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
@DisplayName("Setup two factor use case")
class SetupTwoFactorUseCaseTest {

    @Mock UserGateway userGateway;
    @Mock UserTokenGateway userTokenGateway;
    @Mock TotpGateway totpGateway;
    @Mock StringEncryptor stringEncryptor;
    @Mock TransactionManager transactionManager;

    SetupTwoFactorUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SetupTwoFactorUseCase(
                userGateway, userTokenGateway, totpGateway, stringEncryptor, transactionManager
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUserWithout2FA() {
        return User.with(
                UserId.unique(), "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildUserWith2FAEnabled() {
        return User.with(
                UserId.unique(), "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, true, "totp-secret",
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildSetupToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR_SETUP.name(), "encrypted-secret",
                Instant.now().plusSeconds(900),
                null, Instant.now()
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve gerar secret e retornar setupTokenId quando o usuario nao tem 2FA")
    void givenUserWithout2FA_whenExecute_thenReturnSetupOutput() {
        final var user = buildUserWithout2FA();
        final var setupToken = buildSetupToken(user.getId());

        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.generateSecret()).thenReturn("BASE32SECRET");
        when(totpGateway.getUriForImage(eq("BASE32SECRET"), eq(user.getEmail()), any()))
                .thenReturn("otpauth://totp/BTree:john@example.com?secret=BASE32SECRET");
        when(stringEncryptor.encrypt("BASE32SECRET")).thenReturn("encrypted-secret");
        when(userTokenGateway.create(any())).thenReturn(setupToken);

        final var result = useCase.execute(new SetupTwoFactorCommand(user.getId().getValue().toString()));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(setupToken.getId().getValue().toString(), output.setupTokenId());
        assertEquals("BASE32SECRET", output.secret());
        assertEquals("otpauth://totp/BTree:john@example.com?secret=BASE32SECRET", output.qrCodeUri());
    }

    @Test
    @DisplayName("Deve retornar erro quando o usuario nao for encontrado")
    void givenUnknownUser_whenExecute_thenReturnUserNotFound() {
        when(userGateway.findById(any())).thenReturn(Optional.empty());

        final var result = useCase.execute(new SetupTwoFactorCommand(UUID.randomUUID().toString()));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o 2FA ja estiver habilitado")
    void givenUserWith2FAAlreadyEnabled_whenExecute_thenReturnTwoFactorAlreadyEnabled() {
        final var user = buildUserWith2FAEnabled();
        when(userGateway.findById(any())).thenReturn(Optional.of(user));

        final var result = useCase.execute(new SetupTwoFactorCommand(user.getId().getValue().toString()));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TWO_FACTOR_ALREADY_ENABLED.message())));
    }

    @Test
    @DisplayName("Deve invalidar tokens de setup ativos anteriores antes de criar o novo")
    void givenExistingSetupToken_whenExecute_thenInvalidatePreviousTokens() {
        final var user = buildUserWithout2FA();
        final var setupToken = buildSetupToken(user.getId());

        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.generateSecret()).thenReturn("BASE32SECRET");
        when(totpGateway.getUriForImage(any(), any(), any())).thenReturn("otpauth://totp/...");
        when(stringEncryptor.encrypt(any())).thenReturn("encrypted-secret");
        when(userTokenGateway.create(any())).thenReturn(setupToken);

        useCase.execute(new SetupTwoFactorCommand(user.getId().getValue().toString()));

        verify(userTokenGateway).invalidateActiveByUserIdAndType(
                eq(user.getId()), eq(TokenType.TWO_FACTOR_SETUP.name())
        );
    }

    @Test
    @DisplayName("Deve criptografar o secret antes de persistir o token de setup")
    void givenValidUser_whenExecute_thenEncryptSecretBeforePersisting() {
        final var user = buildUserWithout2FA();
        final var setupToken = buildSetupToken(user.getId());

        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.generateSecret()).thenReturn("RAW-SECRET");
        when(totpGateway.getUriForImage(any(), any(), any())).thenReturn("otpauth://totp/...");
        when(stringEncryptor.encrypt("RAW-SECRET")).thenReturn("ENCRYPTED-SECRET");
        when(userTokenGateway.create(any())).thenReturn(setupToken);

        useCase.execute(new SetupTwoFactorCommand(user.getId().getValue().toString()));

        verify(stringEncryptor).encrypt("RAW-SECRET");
        verify(userTokenGateway).create(argThat(t ->
                "ENCRYPTED-SECRET".equals(t.getTokenHash()) &&
                TokenType.TWO_FACTOR_SETUP.name().equals(t.getTokenType()) &&
                t.getUserId().equals(user.getId())
        ));
    }

    @Test
    @DisplayName("Deve retornar left quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnLeft() {
        final var user = buildUserWithout2FA();

        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(totpGateway.generateSecret()).thenReturn("BASE32SECRET");
        when(totpGateway.getUriForImage(any(), any(), any())).thenReturn("otpauth://totp/...");
        when(stringEncryptor.encrypt(any())).thenReturn("encrypted-secret");
        doThrow(new RuntimeException("DB error")).when(transactionManager).execute(any());

        final var result = useCase.execute(new SetupTwoFactorCommand(user.getId().getValue().toString()));

        assertTrue(result.isLeft());
    }
}
