package com.btree.application.usecase.user.auth.verify_email;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.enums.TokenType;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VerifyEmailUseCase")
class VerifyEmailUseCaseTest extends UseCaseTest {

    @Test
    @DisplayName("deve verificar e-mail e marcar token como usado")
    void shouldVerifyEmailAndMarkTokenAsUsed() {
        final var user = validUser();
        final var token = emailVerificationToken(user.getId());
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var userTokenGateway = new FakeUserTokenGateway();
        userTokenGateway.token = token;
        final var useCase = newUseCase(userTokenGateway, userGateway);

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isRight());
        assertEquals("hashed-raw-email-token", userTokenGateway.findByTokenHashValue);
        assertEquals(1, userTokenGateway.updateCalls);
        assertEquals(token, userTokenGateway.updatedToken);
        assertNotNull(userTokenGateway.updatedToken.getUsedAt());
        assertEquals(1, userGateway.findByIdCalls);
        assertEquals(user.getId(), userGateway.findByIdValue);
        assertEquals(1, userGateway.updateCalls);
        assertTrue(userGateway.updatedUser.isEmailVerified());
    }

    @Test
    @DisplayName("deve retornar erro quando token nao existe")
    void shouldReturnErrorWhenTokenDoesNotExist() {
        final var userGateway = new FakeUserGateway();
        final var userTokenGateway = new FakeUserTokenGateway();
        final var useCase = newUseCase(userTokenGateway, userGateway);

        final var result = useCase.execute(new VerifyEmailCommand("missing-token"));

        assertTrue(result.isLeft());
        assertEquals("Token inválido ou não encontrado", firstError(result.getLeft()));
        assertEquals("hashed-missing-token", userTokenGateway.findByTokenHashValue);
        assertEquals(0, userGateway.findByIdCalls);
        assertEquals(0, userTokenGateway.updateCalls);
        assertEquals(0, userGateway.updateCalls);
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
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var userTokenGateway = new FakeUserTokenGateway();
        userTokenGateway.token = token;
        final var useCase = newUseCase(userTokenGateway, userGateway);

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isLeft());
        assertEquals("Tipo de token inválido para esta operação", firstError(result.getLeft()));
        assertEquals(0, userGateway.findByIdCalls);
        assertEquals(0, userTokenGateway.updateCalls);
        assertEquals(0, userGateway.updateCalls);
    }

    @Test
    @DisplayName("deve retornar erro quando e-mail ja esta verificado")
    void shouldReturnErrorWhenEmailIsAlreadyVerified() {
        final var user = validUser();
        user.verifyEmail();
        final var token = emailVerificationToken(user.getId());
        final var userGateway = new FakeUserGateway();
        userGateway.user = user;
        final var userTokenGateway = new FakeUserTokenGateway();
        userTokenGateway.token = token;
        final var useCase = newUseCase(userTokenGateway, userGateway);

        final var result = useCase.execute(new VerifyEmailCommand("raw-email-token"));

        assertTrue(result.isLeft());
        assertEquals("E-mail já verificado", firstError(result.getLeft()));
        assertEquals(1, userGateway.findByIdCalls);
        assertEquals(0, userTokenGateway.updateCalls);
        assertEquals(0, userGateway.updateCalls);
    }

    private static VerifyEmailUseCase newUseCase(
            final UserTokenGateway userTokenGateway,
            final UserGateway userGateway
    ) {
        return new VerifyEmailUseCase(
                userTokenGateway,
                userGateway,
                new FakeTokenHasher(),
                new ImmediateTransactionManager()
        );
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

    private static final class FakeUserTokenGateway implements UserTokenGateway {
        private UserToken token;
        private String findByTokenHashValue;
        private int updateCalls;
        private UserToken updatedToken;

        @Override
        public UserToken create(final UserToken userToken) {
            return userToken;
        }

        @Override
        public UserToken update(final UserToken userToken) {
            this.updateCalls++;
            this.updatedToken = userToken;
            return userToken;
        }

        @Override
        public Optional<UserToken> findByTokenHash(final String tokenHash) {
            this.findByTokenHashValue = tokenHash;
            return Optional.ofNullable(token);
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

    private static final class FakeUserGateway implements UserGateway {
        private User user;
        private int findByIdCalls;
        private UserId findByIdValue;
        private int updateCalls;
        private User updatedUser;

        @Override
        public User save(final User user) {
            return user;
        }

        @Override
        public boolean existsByUsername(final String username) {
            return false;
        }

        @Override
        public boolean existsByEmail(final String email) {
            return false;
        }

        @Override
        public void assignRole(final UserId userId, final String roleName) {
        }

        @Override
        public Optional<User> findByEmail(final String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(final UserId id) {
            this.findByIdCalls++;
            this.findByIdValue = id;
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<User> findByUsernameOrEmail(final String identifier) {
            return Optional.empty();
        }

        @Override
        public User update(final User user) {
            this.updateCalls++;
            this.updatedUser = user;
            return user;
        }
    }

    private static final class FakeTokenHasher implements TokenHasher {
        @Override
        public String generate() {
            return "raw-email-token";
        }

        @Override
        public String hash(final String token) {
            return "hashed-" + token;
        }
    }

}
