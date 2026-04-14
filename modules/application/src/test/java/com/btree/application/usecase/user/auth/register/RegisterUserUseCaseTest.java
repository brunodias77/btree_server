package com.btree.application.usecase.user.auth.register;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.EmailService;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.event.IntegrationEventPublisher;
import com.btree.shared.event.user.UserRegisteredIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RegisterUserUseCase")
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest extends UseCaseTest {

    @Mock private UserGateway userGateway;
    @Mock private UserTokenGateway userTokenGateway;
    @Mock private PasswordHasher passwordHasher;
    @Mock private TokenHasher tokenHasher;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private EmailService emailService;

    @Captor private ArgumentCaptor<User> userCaptor;

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(
                userGateway,
                userTokenGateway,
                passwordHasher,
                tokenHasher,
                domainEventPublisher,
                integrationEventPublisher,
                new ImmediateTransactionManager(),
                emailService
        );
    }

    @Test
    @DisplayName("deve registrar usuario, criar token de verificacao e enviar e-mail")
    void shouldRegisterUserAndSendVerificationEmail() {
        when(userGateway.existsByUsername("brunodias")).thenReturn(false);
        when(userGateway.existsByEmail("bruno@example.com")).thenReturn(false);
        when(passwordHasher.hash("StrongPassword123!")).thenReturn("hashed-StrongPassword123!");
        when(tokenHasher.generate()).thenReturn("raw-verification-token");
        when(tokenHasher.hash("raw-verification-token")).thenReturn("hashed-raw-verification-token");
        when(userGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userTokenGateway.create(any())).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(new RegisterUserCommand(
                "BrunoDias",
                "BRUNO@EXAMPLE.COM",
                "StrongPassword123!"
        ));

        assertTrue(result.isRight());
        assertEquals("brunodias", result.get().username());
        assertEquals("bruno@example.com", result.get().email());
        assertNotNull(result.get().userId());
        assertNotNull(result.get().createdAt());

        verify(userGateway).save(userCaptor.capture());
        final var savedUser = userCaptor.getValue();
        assertEquals("brunodias", savedUser.getUsername());
        assertEquals("bruno@example.com", savedUser.getEmail());
        assertEquals("hashed-StrongPassword123!", savedUser.getPasswordHash());
        verify(userGateway).assignRole(eq(savedUser.getId()), eq("customer"));

        verify(userTokenGateway).create(argThat(t ->
                savedUser.getId().equals(t.getUserId()) &&
                TokenType.EMAIL_VERIFICATION.name().equals(t.getTokenType()) &&
                "hashed-raw-verification-token".equals(t.getTokenHash())
        ));

        verify(domainEventPublisher).publishAll(argThat(events -> !events.isEmpty()));

        final var eventCaptor = ArgumentCaptor.forClass(com.btree.shared.event.IntegrationEvent.class);
        verify(integrationEventPublisher).publish(eventCaptor.capture());
        final var integrationEvent = assertInstanceOf(UserRegisteredIntegrationEvent.class, eventCaptor.getValue());
        assertEquals(savedUser.getId().getValue(), integrationEvent.getUserId());
        assertEquals("bruno@example.com", integrationEvent.getEmail());

        verify(emailService).sendEmailVerification("bruno@example.com", "brunodias", "raw-verification-token");
    }

    @Test
    @DisplayName("deve acumular erros de username e e-mail duplicados sem persistir")
    void shouldReturnDuplicatedUsernameAndEmailWithoutPersisting() {
        when(userGateway.existsByUsername("brunodias")).thenReturn(true);
        when(userGateway.existsByEmail("bruno@example.com")).thenReturn(true);

        final var result = useCase.execute(new RegisterUserCommand(
                "BrunoDias",
                "BRUNO@EXAMPLE.COM",
                "StrongPassword123!"
        ));

        assertTrue(result.isLeft());
        assertEquals(List.of(
                "Nome de usuário já está em uso",
                "E-mail já está em uso"
        ), errors(result.getLeft()));

        verify(userGateway, never()).save(any());
        verify(userGateway, never()).assignRole(any(), any());
        verify(userTokenGateway, never()).create(any());
        verify(passwordHasher, never()).hash(any());
        verify(tokenHasher, never()).generate();
        verify(domainEventPublisher, never()).publishAll(any());
        verify(integrationEventPublisher, never()).publish(any());
        verify(emailService, never()).sendEmailVerification(any(), any(), any());
    }
}
