package com.btree.application.usecase.user.auth.verify_email;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.enums.TokenType;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VerifyEmailUseCase")
@ExtendWith(MockitoExtension.class)
class VerifyEmailUseCaseTest extends UseCaseTest {

    @Mock private UserTokenGateway userTokenGateway;
    @Mock private UserGateway userGateway;
    @Mock private TokenHasher tokenHasher;

    private VerifyEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyEmailUseCase(
                userTokenGateway,
                userGateway,
                tokenHasher,
                new ImmediateTransactionManager()
        );
    }

    @Test
    @DisplayName("deve verificar e-mail e marcar token como usado")
    void shouldVerifyEmailAndMarkTokenAsUsed() {
        final var user = validUser();
        final var token = emailVerificationToken(user.getId());
        when(tokenHasher.hash("raw-email-token")).thenReturn("hashed-raw-email-token");
        when(userTokenGateway.findByTokenHash("hashed-raw-email-token")).thenReturn(Optional.of(token));
        when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));
        when(userTokenGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userGateway.update(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isRight());
        verify(userTokenGateway).findByTokenHash("hashed-raw-email-token");
        verify(userGateway).findById(user.getId());
        verify(userTokenGateway).update(argThat(t -> t.getUsedAt() != null));
        verify(userGateway).update(argThat(User::isEmailVerified));
    }

    @Test
    @DisplayName("deve retornar erro quando token nao existe")
    void shouldReturnErrorWhenTokenDoesNotExist() {
        when(tokenHasher.hash("missing-token")).thenReturn("hashed-missing-token");
        when(userTokenGateway.findByTokenHash("hashed-missing-token")).thenReturn(Optional.empty());

        final var result = useCase.execute(new VerifyEmailCommand("missing-token"));

        assertTrue(result.isLeft());
        assertEquals("Token inválido ou não encontrado", firstError(result.getLeft()));
        verify(userTokenGateway).findByTokenHash("hashed-missing-token");
        verify(userGateway, never()).findById(any());
        verify(userTokenGateway, never()).update(any());
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve retornar erro quando token possui tipo invalido")
    void shouldReturnErrorWhenTokenTypeIsInvalid() {
        final var user = validUser();
        final var token = UserToken.create(
                user.getId(),
                TokenType.PASSWORD_RESET.name(),
                "hashed-raw-email-token",
                Instant.now().plusSeconds(3600)
        );
        when(tokenHasher.hash("raw-email-token")).thenReturn("hashed-raw-email-token");
        when(userTokenGateway.findByTokenHash("hashed-raw-email-token")).thenReturn(Optional.of(token));

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isLeft());
        assertEquals("Tipo de token inválido para esta operação", firstError(result.getLeft()));
        verify(userGateway, never()).findById(any());
        verify(userTokenGateway, never()).update(any());
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("deve retornar erro quando e-mail ja esta verificado")
    void shouldReturnErrorWhenEmailIsAlreadyVerified() {
        final var user = validUser();
        user.verifyEmail();
        final var token = emailVerificationToken(user.getId());
        when(tokenHasher.hash("raw-email-token")).thenReturn("hashed-raw-email-token");
        when(userTokenGateway.findByTokenHash("hashed-raw-email-token")).thenReturn(Optional.of(token));
        when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isLeft());
        assertEquals("E-mail já verificado", firstError(result.getLeft()));
        verify(userGateway).findById(user.getId());
        verify(userTokenGateway, never()).update(any());
        verify(userGateway, never()).update(any());
    }

    private static User validUser() {
        return User.create(
                "brunodias",
                "bruno@example.com",
                "StrongPassword123!",
                Notification.create()
        );
    }

    private static UserToken emailVerificationToken(final UserId userId) {
        return UserToken.create(
                userId,
                TokenType.EMAIL_VERIFICATION.name(),
                "hashed-raw-email-token",
                Instant.now().plusSeconds(3600)
        );
    }
}
