package com.btree.application.usecase.user.auth.register;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.EmailService;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.domain.DomainEvent;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.event.IntegrationEvent;
import com.btree.shared.event.IntegrationEventPublisher;
import com.btree.shared.event.user.UserRegisteredIntegrationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RegisterUserUseCase")
class RegisterUserUseCaseTest extends UseCaseTest {

    @Test
    @DisplayName("deve registrar usuario, criar token de verificacao e enviar e-mail")
    void shouldRegisterUserAndSendVerificationEmail() {
        final var userGateway = new FakeUserGateway();
        final var userTokenGateway = new FakeUserTokenGateway();
        final var passwordHasher = new FakePasswordHasher();
        final var tokenHasher = new FakeTokenHasher();
        final var domainEventPublisher = new FakeDomainEventPublisher();
        final var integrationEventPublisher = new FakeIntegrationEventPublisher();
        final var emailService = new FakeEmailService();
        final var useCase = newUseCase(
                userGateway,
                userTokenGateway,
                passwordHasher,
                tokenHasher,
                domainEventPublisher,
                integrationEventPublisher,
                emailService
        );

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

        assertEquals(1, userGateway.saveCalls);
        assertEquals("brunodias", userGateway.savedUser.getUsername());
        assertEquals("bruno@example.com", userGateway.savedUser.getEmail());
        assertEquals("hashed-StrongPassword123!", userGateway.savedUser.getPasswordHash());
        assertEquals(userGateway.savedUser.getId(), userGateway.assignedRoleUserId);
        assertEquals("customer", userGateway.assignedRoleName);

        assertEquals(1, userTokenGateway.createCalls);
        assertEquals(userGateway.savedUser.getId(), userTokenGateway.createdToken.getUserId());
        assertEquals(TokenType.EMAIL_VERIFICATION.name(), userTokenGateway.createdToken.getTokenType());
        assertEquals("hashed-raw-verification-token", userTokenGateway.createdToken.getTokenHash());

        assertEquals(1, domainEventPublisher.publishAllCalls);
        assertFalse(domainEventPublisher.publishedEvents.isEmpty());

        assertEquals(1, integrationEventPublisher.publishCalls);
        final var integrationEvent = assertInstanceOf(
                UserRegisteredIntegrationEvent.class,
                integrationEventPublisher.publishedEvent
        );
        assertEquals(userGateway.savedUser.getId().getValue(), integrationEvent.getUserId());
        assertEquals("bruno@example.com", integrationEvent.getEmail());

        assertEquals(1, emailService.verificationCalls);
        assertEquals("bruno@example.com", emailService.toEmail);
        assertEquals("brunodias", emailService.username);
        assertEquals("raw-verification-token", emailService.rawToken);
    }

    @Test
    @DisplayName("deve acumular erros de username e e-mail duplicados sem persistir")
    void shouldReturnDuplicatedUsernameAndEmailWithoutPersisting() {
        final var userGateway = new FakeUserGateway();
        userGateway.usernameExists = true;
        userGateway.emailExists = true;
        final var userTokenGateway = new FakeUserTokenGateway();
        final var passwordHasher = new FakePasswordHasher();
        final var tokenHasher = new FakeTokenHasher();
        final var domainEventPublisher = new FakeDomainEventPublisher();
        final var integrationEventPublisher = new FakeIntegrationEventPublisher();
        final var emailService = new FakeEmailService();
        final var useCase = newUseCase(
                userGateway,
                userTokenGateway,
                passwordHasher,
                tokenHasher,
                domainEventPublisher,
                integrationEventPublisher,
                emailService
        );

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

        assertEquals(0, userGateway.saveCalls);
        assertEquals(0, userGateway.assignRoleCalls);
        assertEquals(0, userTokenGateway.createCalls);
        assertEquals(0, passwordHasher.hashCalls);
        assertEquals(0, tokenHasher.generateCalls);
        assertEquals(0, domainEventPublisher.publishAllCalls);
        assertEquals(0, integrationEventPublisher.publishCalls);
        assertEquals(0, emailService.verificationCalls);
    }

    private static RegisterUserUseCase newUseCase(
            final UserGateway userGateway,
            final UserTokenGateway userTokenGateway,
            final PasswordHasher passwordHasher,
            final TokenHasher tokenHasher,
            final DomainEventPublisher domainEventPublisher,
            final IntegrationEventPublisher integrationEventPublisher,
            final EmailService emailService
    ) {
        return new RegisterUserUseCase(
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

    private static final class FakeUserGateway implements UserGateway {
        private boolean usernameExists;
        private boolean emailExists;
        private int saveCalls;
        private int assignRoleCalls;
        private User savedUser;
        private UserId assignedRoleUserId;
        private String assignedRoleName;

        @Override
        public User save(final User user) {
            this.saveCalls++;
            this.savedUser = user;
            return user;
        }

        @Override
        public boolean existsByUsername(final String username) {
            assertEquals("brunodias", username);
            return usernameExists;
        }

        @Override
        public boolean existsByEmail(final String email) {
            assertEquals("bruno@example.com", email);
            return emailExists;
        }

        @Override
        public void assignRole(final UserId userId, final String roleName) {
            this.assignRoleCalls++;
            this.assignedRoleUserId = userId;
            this.assignedRoleName = roleName;
        }

        @Override
        public Optional<User> findByEmail(final String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(final UserId id) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsernameOrEmail(final String identifier) {
            return Optional.empty();
        }

        @Override
        public User update(final User user) {
            return user;
        }
    }

    private static final class FakeUserTokenGateway implements UserTokenGateway {
        private int createCalls;
        private UserToken createdToken;

        @Override
        public UserToken create(final UserToken userToken) {
            this.createCalls++;
            this.createdToken = userToken;
            return userToken;
        }

        @Override
        public UserToken update(final UserToken userToken) {
            return userToken;
        }

        @Override
        public Optional<UserToken> findByTokenHash(final String tokenHash) {
            return Optional.empty();
        }

        @Override
        public Optional<UserToken> findById(final UserTokenId id) {
            return Optional.empty();
        }

        @Override
        public int deleteExpired(final int batchSize) {
            return 0;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        private int hashCalls;

        @Override
        public String hash(final String rawPassword) {
            this.hashCalls++;
            return "hashed-" + rawPassword;
        }

        @Override
        public boolean matches(final String rawPassword, final String hashedPassword) {
            return false;
        }
    }

    private static final class FakeTokenHasher implements TokenHasher {
        private int generateCalls;

        @Override
        public String generate() {
            this.generateCalls++;
            return "raw-verification-token";
        }

        @Override
        public String hash(final String token) {
            return "hashed-" + token;
        }
    }

    private static final class FakeDomainEventPublisher implements DomainEventPublisher {
        private int publishAllCalls;
        private final List<DomainEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publish(final DomainEvent event) {
            this.publishedEvents.add(event);
        }

        @Override
        public void publishAll(final List<? extends DomainEvent> events) {
            this.publishAllCalls++;
            this.publishedEvents.addAll(events);
        }
    }

    private static final class FakeIntegrationEventPublisher implements IntegrationEventPublisher {
        private int publishCalls;
        private IntegrationEvent publishedEvent;

        @Override
        public void publish(final IntegrationEvent event) {
            this.publishCalls++;
            this.publishedEvent = event;
        }
    }

    private static final class FakeEmailService implements EmailService {
        private int verificationCalls;
        private String toEmail;
        private String username;
        private String rawToken;

        @Override
        public void sendEmailVerification(
                final String toEmail,
                final String username,
                final String rawToken
        ) {
            this.verificationCalls++;
            this.toEmail = toEmail;
            this.username = username;
            this.rawToken = rawToken;
        }

        @Override
        public void sendPasswordResetEmail(
                final String toEmail,
                final String username,
                final String rawToken
        ) {
        }
    }

    private static final class ImmediateTransactionManager implements TransactionManager {
        @Override
        public <T> T execute(final Supplier<T> action) {
            return action.get();
        }

        @Override
        public void executeVoid(final Runnable action) {
            action.run();
        }
    }
}
