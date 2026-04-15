package com.btree.application.usecase.user.auth.register;

import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.EmailService;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.event.IntegrationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock UserGateway userGateway;
    @Mock UserTokenGateway userTokenGateway;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenHasher tokenHasher;
    @Mock DomainEventPublisher eventPublisher;
    @Mock IntegrationEventPublisher integrationEventPublisher;
    @Mock TransactionManager transactionManager;
    @Mock EmailService emailService;

    RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(
                userGateway, userTokenGateway, passwordHasher, tokenHasher,
                eventPublisher, integrationEventPublisher, transactionManager, emailService
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildSavedUser(String username, String email) {
        return User.with(
                UserId.unique(), username, email,
                false, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserToken buildVerificationToken(UserId userId) {
        return UserToken.with(
                UserTokenId.unique(), userId,
                TokenType.EMAIL_VERIFICATION.name(), "token-hash",
                Instant.now().plusSeconds(86400),
                null, Instant.now()
        );
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    void givenValidCommand_whenExecute_thenCreateUserSuccessfully() {
        final var savedUser = buildSavedUser("johndoe", "john@example.com");
        final var token = buildVerificationToken(savedUser.getId());

        when(userGateway.existsByUsername("johndoe")).thenReturn(false);
        when(userGateway.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordHasher.hash(anyString())).thenReturn("$2a$10$Hashed1Pass");
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        when(userGateway.save(any())).thenReturn(savedUser);
        doNothing().when(userGateway).assignRole(any(), anyString());
        when(userTokenGateway.create(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
        doNothing().when(integrationEventPublisher).publish(any());
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());

        final var command = new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass");
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(savedUser.getId().getValue().toString(), output.userId());
        assertEquals("johndoe", output.username());
        assertEquals("john@example.com", output.email());
        assertNotNull(output.createdAt());
    }

    @Test
    void givenValidCommand_whenExecute_thenNormalizesToLowercase() {
        final var savedUser = buildSavedUser("johndoe", "john@example.com");
        final var token = buildVerificationToken(savedUser.getId());

        when(userGateway.existsByUsername("johndoe")).thenReturn(false);
        when(userGateway.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordHasher.hash(anyString())).thenReturn("$2a$10$Hashed1Pass");
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userGateway.save(any())).thenReturn(savedUser);
        doNothing().when(userGateway).assignRole(any(), anyString());
        when(userTokenGateway.create(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
        doNothing().when(integrationEventPublisher).publish(any());
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());

        useCase.execute(new RegisterUserCommand("JohnDoe", "JOHN@EXAMPLE.COM", "Str0ng!Pass"));

        verify(userGateway).existsByUsername("johndoe");
        verify(userGateway).existsByEmail("john@example.com");
    }

    @Test
    void givenWeakPassword_whenExecute_thenReturnPasswordError() {
        final var result = useCase.execute(
                new RegisterUserCommand("johndoe", "john@example.com", "weakpass")
        );

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(userGateway, never()).save(any());
    }

    @Test
    void givenExistingUsername_whenExecute_thenReturnUsernameError() {
        when(userGateway.existsByUsername("johndoe")).thenReturn(true);
        when(userGateway.existsByEmail(anyString())).thenReturn(false);

        final var result = useCase.execute(
                new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass")
        );

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USERNAME_ALREADY_EXISTS.message())));
        verify(userGateway, never()).save(any());
    }

    @Test
    void givenExistingEmail_whenExecute_thenReturnEmailError() {
        when(userGateway.existsByUsername(anyString())).thenReturn(false);
        when(userGateway.existsByEmail("john@example.com")).thenReturn(true);

        final var result = useCase.execute(
                new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass")
        );

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.EMAIL_ALREADY_EXISTS.message())));
        verify(userGateway, never()).save(any());
    }

    @Test
    void givenBothExistingUsernameAndEmail_whenExecute_thenReturnBothErrors() {
        when(userGateway.existsByUsername("johndoe")).thenReturn(true);
        when(userGateway.existsByEmail("john@example.com")).thenReturn(true);

        final var result = useCase.execute(
                new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass")
        );

        assertTrue(result.isLeft());
        final var errors = result.getLeft().getErrors();
        assertTrue(errors.stream().anyMatch(e -> e.message().equals(UserError.USERNAME_ALREADY_EXISTS.message())));
        assertTrue(errors.stream().anyMatch(e -> e.message().equals(UserError.EMAIL_ALREADY_EXISTS.message())));
    }

    @Test
    void givenValidCommand_whenExecute_thenAssignCustomerRole() {
        final var savedUser = buildSavedUser("johndoe", "john@example.com");
        final var token = buildVerificationToken(savedUser.getId());

        when(userGateway.existsByUsername(anyString())).thenReturn(false);
        when(userGateway.existsByEmail(anyString())).thenReturn(false);
        when(passwordHasher.hash(anyString())).thenReturn("$2a$10$Hashed1Pass");
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userGateway.save(any())).thenReturn(savedUser);
        doNothing().when(userGateway).assignRole(any(), anyString());
        when(userTokenGateway.create(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
        doNothing().when(integrationEventPublisher).publish(any());
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());

        useCase.execute(new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass"));

        verify(userGateway).assignRole(eq(savedUser.getId()), eq("customer"));
    }

    @Test
    void givenValidCommand_whenExecute_thenSendVerificationEmail() {
        final var savedUser = buildSavedUser("johndoe", "john@example.com");
        final var token = buildVerificationToken(savedUser.getId());

        when(userGateway.existsByUsername(anyString())).thenReturn(false);
        when(userGateway.existsByEmail(anyString())).thenReturn(false);
        when(passwordHasher.hash(anyString())).thenReturn("$2a$10$Hashed1Pass");
        when(tokenHasher.generate()).thenReturn("raw-token");
        when(tokenHasher.hash(anyString())).thenReturn("token-hash");
        when(userGateway.save(any())).thenReturn(savedUser);
        doNothing().when(userGateway).assignRole(any(), anyString());
        when(userTokenGateway.create(any())).thenReturn(token);
        doNothing().when(eventPublisher).publishAll(any());
        doNothing().when(integrationEventPublisher).publish(any());
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());

        useCase.execute(new RegisterUserCommand("johndoe", "john@example.com", "Str0ng!Pass"));

        verify(emailService).sendEmailVerification(
                eq("john@example.com"),
                eq("johndoe"),
                eq("raw-token")
        );
    }
}
