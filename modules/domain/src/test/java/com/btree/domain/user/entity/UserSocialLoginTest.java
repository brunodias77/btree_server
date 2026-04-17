package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserSocialLoginId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserSocialLogin (Entity)")
public class UserSocialLoginTest extends UnitTest {

    private static UserId anyUserId() {
        return UserId.unique();
    }

    private static UserSocialLogin validSocialLogin() {
        return UserSocialLogin.create(anyUserId(), "GOOGLE", "google-uid-123", "Bruno Dias");
    }

    // ─────────────────────────────────────────────────────────
    // create()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — campos e defaults")
    class CreateFactory {

        @Test
        @DisplayName("deve gerar ID único e não nulo")
        void deveGerarIdUnico() {
            final var a = validSocialLogin();
            final var b = UserSocialLogin.create(anyUserId(), "FACEBOOK", "fb-uid-456", null);
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir userId, provider, providerUserId e providerDisplayName")
        void devePersistirCampos() {
            final var userId = anyUserId();
            final var login  = UserSocialLogin.create(userId, "GOOGLE", "google-uid-123", "Bruno Dias");

            assertEquals(userId,          login.getUserId());
            assertEquals("GOOGLE",        login.getProvider());
            assertEquals("google-uid-123", login.getProviderUserId());
            assertEquals("Bruno Dias",    login.getProviderDisplayName());
        }

        @Test
        @DisplayName("deve aceitar providerDisplayName nulo — campo opcional")
        void deveAceitarDisplayNameNulo() {
            final var login = UserSocialLogin.create(anyUserId(), "APPLE", "apple-uid-789", null);
            assertNull(login.getProviderDisplayName());
        }

        @Test
        @DisplayName("deve preencher createdAt próximo ao instante de criação")
        void devePreencherCreatedAt() {
            final var before = Instant.now();
            final var login  = validSocialLogin();
            final var after  = Instant.now();

            assertNotNull(login.getCreatedAt());
            assertFalse(login.getCreatedAt().isBefore(before));
            assertFalse(login.getCreatedAt().isAfter(after));
        }
    }

    // ─────────────────────────────────────────────────────────
    // create() — guards de NPE
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — validação de campos obrigatórios (NullPointerException)")
    class CreateNpeGuards {

        @Test
        @DisplayName("deve lançar NullPointerException quando userId for nulo")
        void deveLancarExcecaoComUserIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserSocialLogin.create(null, "GOOGLE", "uid", "Nome"));
        }

        @Test
        @DisplayName("deve lançar NullPointerException quando provider for nulo")
        void deveLancarExcecaoComProviderNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserSocialLogin.create(anyUserId(), null, "uid", "Nome"));
        }

        @Test
        @DisplayName("deve lançar NullPointerException quando providerUserId for nulo")
        void deveLancarExcecaoComProviderUserIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserSocialLogin.create(anyUserId(), "GOOGLE", null, "Nome"));
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos fornecidos")
        void deveReconstituirTodosCampos() {
            final var id        = UserSocialLoginId.unique();
            final var userId    = anyUserId();
            final var createdAt = Instant.now().minusSeconds(1800);

            final var login = UserSocialLogin.with(
                    id, userId, "GITHUB", "github-uid-999", "octocat", createdAt
            );

            assertEquals(id,            login.getId());
            assertEquals(userId,        login.getUserId());
            assertEquals("GITHUB",      login.getProvider());
            assertEquals("github-uid-999", login.getProviderUserId());
            assertEquals("octocat",     login.getProviderDisplayName());
            assertEquals(createdAt,     login.getCreatedAt());
        }

        @Test
        @DisplayName("com() deve lançar NullPointerException quando userId for nulo")
        void deveLancarExcecaoComUserIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserSocialLogin.with(UserSocialLoginId.unique(), null, "GOOGLE", "uid", null, Instant.now()));
        }
    }
}
