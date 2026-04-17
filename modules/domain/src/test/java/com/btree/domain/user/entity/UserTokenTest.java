package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserToken (Entity)")
public class UserTokenTest extends UnitTest {

    private static final UserId   VALID_USER_ID  = UserId.unique();
    private static final String   VALID_TYPE     = "EMAIL_VERIFICATION";
    private static final String   VALID_HASH     = "hashed_token_value_abc123";
    private static final Instant  FUTURE_EXPIRY  = Instant.now().plusSeconds(3600);

    private static UserToken validToken() {
        return UserToken.create(VALID_USER_ID, VALID_TYPE, VALID_HASH, FUTURE_EXPIRY);
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
            final var a = validToken();
            final var b = UserToken.create(UserId.unique(), "PASSWORD_RESET", "outro_hash", FUTURE_EXPIRY);
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir userId, tokenType, tokenHash e expiresAt")
        void devePersistirCampos() {
            final var token = validToken();

            assertEquals(VALID_USER_ID, token.getUserId());
            assertEquals(VALID_TYPE,    token.getTokenType());
            assertEquals(VALID_HASH,    token.getTokenHash());
            assertEquals(FUTURE_EXPIRY, token.getExpiresAt());
        }

        @Test
        @DisplayName("deve inicializar usedAt como nulo")
        void deveInicializarUsedAtNulo() {
            assertNull(validToken().getUsedAt());
        }

        @Test
        @DisplayName("deve preencher createdAt próximo ao instante de criação")
        void devePreencherCreatedAt() {
            final var before = Instant.now();
            final var token  = validToken();
            final var after  = Instant.now();

            assertNotNull(token.getCreatedAt());
            assertFalse(token.getCreatedAt().isBefore(before));
            assertFalse(token.getCreatedAt().isAfter(after));
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
                    () -> UserToken.create(null, VALID_TYPE, VALID_HASH, FUTURE_EXPIRY));
        }

        @Test
        @DisplayName("deve lançar NullPointerException quando tokenType for nulo")
        void deveLancarExcecaoComTokenTypeNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserToken.create(VALID_USER_ID, null, VALID_HASH, FUTURE_EXPIRY));
        }

        @Test
        @DisplayName("deve lançar NullPointerException quando tokenHash for nulo")
        void deveLancarExcecaoComTokenHashNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserToken.create(VALID_USER_ID, VALID_TYPE, null, FUTURE_EXPIRY));
        }

        @Test
        @DisplayName("deve lançar NullPointerException quando expiresAt for nulo")
        void deveLancarExcecaoComExpiresAtNulo() {
            assertThrows(NullPointerException.class,
                    () -> UserToken.create(VALID_USER_ID, VALID_TYPE, VALID_HASH, null));
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos de um token não utilizado")
        void deveReconstituirTokenNaoUtilizado() {
            final var id        = UserTokenId.unique();
            final var userId    = UserId.unique();
            final var now       = Instant.now();
            final var expiresAt = now.plusSeconds(1800);

            final var token = UserToken.with(id, userId, "PASSWORD_RESET", "some_hash", expiresAt, null, now);

            assertEquals(id,             token.getId());
            assertEquals(userId,         token.getUserId());
            assertEquals("PASSWORD_RESET", token.getTokenType());
            assertEquals("some_hash",    token.getTokenHash());
            assertEquals(expiresAt,      token.getExpiresAt());
            assertNull(token.getUsedAt());
            assertEquals(now,            token.getCreatedAt());
        }

        @Test
        @DisplayName("deve reconstituir token já utilizado com usedAt preenchido")
        void deveReconstituirTokenUtilizado() {
            final var now    = Instant.now();
            final var usedAt = now.minusSeconds(600);

            final var token = UserToken.with(
                    UserTokenId.unique(), UserId.unique(), "EMAIL_VERIFICATION",
                    "hash", now.plusSeconds(3600), usedAt, now
            );

            assertNotNull(token.getUsedAt());
            assertEquals(usedAt, token.getUsedAt());
            assertTrue(token.isUsed());
        }
    }

    // ─────────────────────────────────────────────────────────
    // isExpired() / isUsed() / markAsUsed()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isExpired()")
    class Expiration {

        @Test
        @DisplayName("deve retornar false quando expiresAt estiver no futuro")
        void naoDeveEstarExpiradoQuandoNoFuturo() {
            assertFalse(validToken().isExpired());
        }

        @Test
        @DisplayName("deve retornar true quando expiresAt estiver no passado")
        void deveEstarExpiradoQuandoNoPassado() {
            final var now   = Instant.now();
            final var token = UserToken.with(
                    UserTokenId.unique(), UserId.unique(), VALID_TYPE, VALID_HASH,
                    now.minusSeconds(1), null, now
            );
            assertTrue(token.isExpired());
        }
    }

    @Nested
    @DisplayName("isUsed() e markAsUsed()")
    class UsageState {

        @Test
        @DisplayName("isUsed() deve retornar false para token recém-criado")
        void deveEstarNaoUtilizadoNoInicio() {
            assertFalse(validToken().isUsed());
        }

        @Test
        @DisplayName("markAsUsed() deve preencher usedAt")
        void devePreencherUsedAt() {
            final var token  = validToken();
            final var before = Instant.now();
            token.markAsUsed();
            final var after  = Instant.now();

            assertNotNull(token.getUsedAt());
            assertFalse(token.getUsedAt().isBefore(before));
            assertFalse(token.getUsedAt().isAfter(after));
        }

        @Test
        @DisplayName("isUsed() deve retornar true após markAsUsed()")
        void deveEstarUtilizadoAposMarcar() {
            final var token = validToken();
            token.markAsUsed();
            assertTrue(token.isUsed());
        }
    }
}
