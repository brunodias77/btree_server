package com.btree.application.usecase.user.auth.forgot_password;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.EmailService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Forgot password use case")
class ForgotPasswordUseCaseTest extends UseCaseTest {

    @Mock UserGateway userGateway;
    @Mock UserTokenGateway userTokenGateway;
    @Mock TokenHasher tokenHasher;
    @Mock EmailService emailService;
    @Mock DomainEventPublisher eventPublisher;
    @Mock TransactionManager transactionManager;

    ForgotPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ForgotPasswordUseCase(
                userGateway, userTokenGateway, tokenHasher,
                emailService, eventPublisher, transactionManager
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildEnabledUser(final String username, final String email) {
        return User.with(
                UserId.unique(), username, email,
                true, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private User buildDisabledUser(final String username, final String email) {
        return User.with(
                UserId.unique(), username, email,
                true, "hashed-pw",
                null, false, false, null,
                false, null, 0, false,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildPasswordResetToken(final UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.PASSWORD_RESET.name(), "token-hash",
                Instant.now().plusSeconds(1800),
                null, Instant.now()
        );
    }

    private void stubHappyPath(final User user, final UserToken token) {
        when(userGateway.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        when(userTokenGateway.create(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve criar token e enviar email quando usuario existir e estiver ativo")
    void givenValidEmail_whenExecute_thenCreateTokenAndSendEmail() {
        final var user = buildEnabledUser("joao_silva", "joao@exemplo.com");
        final var token = buildPasswordResetToken(user.getId());
        stubHappyPath(user, token);

        final var result = useCase.execute(new ForgotPasswordCommand("joao@exemplo.com"));

        assertTrue(result.isRight());
        assertNull(result.get());
        verify(userTokenGateway).create(any());
        verify(emailService).sendPasswordResetEmail(
                eq("joao@exemplo.com"), eq("joao_silva"), eq("raw-token")
        );
        verify(eventPublisher).publishAll(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando o email for nulo")
    void givenNullEmail_whenExecute_thenReturnEmailEmptyError() {
        final var result = useCase.execute(new ForgotPasswordCommand(null));

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.EMAIL_EMPTY.message()));
        verify(userGateway, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Deve retornar erro quando o email estiver em branco")
    void givenBlankEmail_whenExecute_thenReturnEmailEmptyError() {
        final var result = useCase.execute(new ForgotPasswordCommand("   "));

        assertTrue(result.isLeft());
        assertTrue(errors(result.getLeft()).contains(UserError.EMAIL_EMPTY.message()));
        verify(userGateway, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Deve retornar sucesso silencioso quando o email nao estiver cadastrado (anti-enumeracao)")
    void givenNonExistentEmail_whenExecute_thenReturnRightSilently() {
        when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());

        final var result = useCase.execute(new ForgotPasswordCommand("inexistente@exemplo.com"));

        assertTrue(result.isRight());
        assertNull(result.get());
        verify(tokenHasher, never()).generate();
        verify(userTokenGateway, never()).create(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve retornar sucesso silencioso quando o usuario estiver desativado (anti-enumeracao)")
    void givenDisabledUser_whenExecute_thenReturnRightSilently() {
        final var user = buildDisabledUser("joao_silva", "joao@exemplo.com");
        when(userGateway.findByEmail("joao@exemplo.com")).thenReturn(Optional.of(user));

        final var result = useCase.execute(new ForgotPasswordCommand("joao@exemplo.com"));

        assertTrue(result.isRight());
        assertNull(result.get());
        verify(tokenHasher, never()).generate();
        verify(userTokenGateway, never()).create(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve normalizar o email para minusculo antes de buscar")
    void givenUppercaseEmail_whenExecute_thenNormalizesToLowercase() {
        when(userGateway.findByEmail("joao@exemplo.com")).thenReturn(Optional.empty());

        useCase.execute(new ForgotPasswordCommand("JOAO@EXEMPLO.COM"));

        verify(userGateway).findByEmail("joao@exemplo.com");
    }

    @Test
    @DisplayName("Deve criar token do tipo PASSWORD_RESET")
    void givenValidEmail_whenExecute_thenCreatesTokenWithCorrectType() {
        final var user = buildEnabledUser("joao_silva", "joao@exemplo.com");
        final var token = buildPasswordResetToken(user.getId());
        stubHappyPath(user, token);

        useCase.execute(new ForgotPasswordCommand("joao@exemplo.com"));

        verify(userTokenGateway).create(argThat(t ->
                TokenType.PASSWORD_RESET.name().equals(t.getTokenType())
        ));
    }

    @Test
    @DisplayName("Deve publicar domain event de solicitacao de reset de senha")
    void givenValidEmail_whenExecute_thenPublishesPasswordResetRequestedEvent() {
        final var user = buildEnabledUser("joao_silva", "joao@exemplo.com");
        final var token = buildPasswordResetToken(user.getId());
        stubHappyPath(user, token);

        useCase.execute(new ForgotPasswordCommand("joao@exemplo.com"));

        verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
    }

    @Test
    @DisplayName("Deve retornar Left quando ocorrer excecao na transacao")
    void givenTransactionException_whenExecute_thenReturnLeft() {
        final var user = buildEnabledUser("joao_silva", "joao@exemplo.com");

        when(userGateway.findByEmail("joao@exemplo.com")).thenReturn(Optional.of(user));
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        doThrow(new RuntimeException("DB failure")).when(transactionManager).execute(any());

        final var result = useCase.execute(new ForgotPasswordCommand("joao@exemplo.com"));

        assertTrue(result.isLeft());
    }
}
