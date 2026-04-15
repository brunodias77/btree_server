package com.btree.application.usecase.user.auth.verify_email;

import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.TokenHasher;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Verify email use case")
class VerifyEmailUseCaseTest {

    @Mock UserTokenGateway userTokenGateway;
    @Mock UserGateway userGateway;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;

    VerifyEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyEmailUseCase(userTokenGateway, userGateway, tokenHasher, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserId userId() {
        return UserId.unique();
    }

    private UserToken buildValidToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.EMAIL_VERIFICATION.name(), "token-hash",
                Instant.now().plusSeconds(86400), // 24h, não expirado
                null, // não usado
                Instant.now()
        );
    }

    private UserToken buildExpiredToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.EMAIL_VERIFICATION.name(), "token-hash",
                Instant.now().minusSeconds(60), // expirado
                null,
                Instant.now()
        );
    }

    private UserToken buildUsedToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.EMAIL_VERIFICATION.name(), "token-hash",
                Instant.now().plusSeconds(86400),
                Instant.now().minusSeconds(3600), // usado há 1h
                Instant.now()
        );
    }

    private UserToken buildWrongTypeToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.TWO_FACTOR.name(), "token-hash",
                Instant.now().plusSeconds(300),
                null,
                Instant.now()
        );
    }

    private User buildUnverifiedUser(UserId userId) {
        return User.with(
                userId, "johndoe", "john@example.com",
                false, "hashed-pw", // emailVerified = false
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildVerifiedUser(UserId userId) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw", // emailVerified = true
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve verificar email quando o token for valido")
    void givenValidToken_whenExecute_thenVerifyEmail() {
        final var uid = userId();
        final var token = buildValidToken(uid);
        final var user = buildUnverifiedUser(uid);

        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash("token-hash")).thenReturn(Optional.of(token));
        when(userGateway.findById(uid)).thenReturn(Optional.of(user));
        when(userTokenGateway.update(any())).thenReturn(token);
        when(userGateway.update(any())).thenReturn(user);

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isRight());
        assertNull(result.get());
        assertTrue(user.isEmailVerified());
        assertNotNull(token.getUsedAt());
    }

    @Test
    @DisplayName("Deve marcar token como usado quando o token for valido")
    void givenValidToken_whenExecute_thenMarkTokenAsUsed() {
        final var uid = userId();
        final var token = buildValidToken(uid);
        final var user = buildUnverifiedUser(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userGateway.findById(any())).thenReturn(Optional.of(user));
        when(userTokenGateway.update(any())).thenReturn(token);
        when(userGateway.update(any())).thenReturn(user);

        useCase.execute(new VerifyEmailCommand("raw-token"));

        verify(userTokenGateway).update(token);
        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Deve retornar erro quando o token nao for encontrado")
    void givenTokenNotFound_whenExecute_thenReturnTokenNotFoundError() {
        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.empty());

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token tiver tipo invalido")
    void givenWrongTokenType_whenExecute_thenReturnTokenInvalidTypeError() {
        final var uid = userId();
        final var token = buildWrongTypeToken(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_INVALID_TYPE.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token estiver expirado")
    void givenExpiredToken_whenExecute_thenReturnTokenExpiredError() {
        final var uid = userId();
        final var token = buildExpiredToken(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_EXPIRED.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o token ja tiver sido usado")
    void givenAlreadyUsedToken_whenExecute_thenReturnTokenAlreadyUsedError() {
        final var uid = userId();
        final var token = buildUsedToken(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.TOKEN_ALREADY_USED.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o usuario nao for encontrado")
    void givenUserNotFound_whenExecute_thenReturnUserNotFoundError() {
        final var uid = userId();
        final var token = buildValidToken(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userGateway.findById(uid)).thenReturn(Optional.empty());

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando o email ja estiver verificado")
    void givenEmailAlreadyVerified_whenExecute_thenReturnEmailAlreadyVerifiedError() {
        final var uid = userId();
        final var token = buildValidToken(uid);
        final var user = buildVerifiedUser(uid);

        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userTokenGateway.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userGateway.findById(uid)).thenReturn(Optional.of(user));

        final var result = useCase.execute(new VerifyEmailCommand("raw-token"));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.EMAIL_ALREADY_VERIFIED.message())));
        verify(userGateway, never()).update(any());
    }
}
