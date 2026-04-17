package com.btree.domain.user.validator;

import com.btree.domain.UnitTest;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários focados nas regras do {@link SessionValidator}.
 *
 * <p>A estratégia é testar o validator de forma indireta via {@code Session.create()},
 * mantendo apenas uma variável por vez fora dos defaults válidos para isolar
 * precisamente qual regra está sendo exercitada.
 */
@DisplayName("Testes do SessionValidator acoplado à Entidade Session")
public class SessionValidatorTest extends UnitTest {

    private static final UserId    OK_USER_ID    = UserId.unique();
    private static final String    OK_TOKEN_HASH = "hashed_refresh_token_value";
    private static final DeviceInfo OK_DEVICE    = DeviceInfo.of("10.0.0.1", "Chrome/120");
    private static final Instant   OK_EXPIRY     = Instant.now().plusSeconds(3600);

    private static DomainException createInvalid(UserId userId, String tokenHash, Instant expiresAt) {
        return assertThrows(DomainException.class,
                () -> Session.create(userId, tokenHash, OK_DEVICE, expiresAt, Notification.create()),
                "Session.create() deveria lançar DomainException");
    }

    private static List<String> messages(DomainException ex) {
        return ex.getErrors().stream().map(Error::message).toList();
    }

    // ─────────────────────────────────────────────────────────
    // Cenário feliz
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve criar sessão sem erros quando todos os campos obrigatórios são válidos")
    void deveCriarSemErros() {
        final var notification = Notification.create();
        assertDoesNotThrow(() -> Session.create(OK_USER_ID, OK_TOKEN_HASH, OK_DEVICE, OK_EXPIRY, notification));
        assertFalse(notification.hasError(), "Notification não deve ter erros para sessão válida");
    }

    // ─────────────────────────────────────────────────────────
    // userId
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("userId — regras de obrigatoriedade")
    class UserIdObrigatorio {

        @Test
        @DisplayName("deve reportar erro quando userId for nulo")
        void deveReportarErroUserIdNulo() {
            final var ex = createInvalid(null, OK_TOKEN_HASH, OK_EXPIRY);
            assertTrue(messages(ex).stream().anyMatch(m -> m.contains("userId")),
                    "Erro deve mencionar 'userId', mas mensagens foram: " + messages(ex));
        }
    }

    // ─────────────────────────────────────────────────────────
    // refreshTokenHash
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshTokenHash — regras de obrigatoriedade")
    class RefreshTokenHashObrigatorio {

        @ParameterizedTest(name = "refreshTokenHash=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("deve reportar erro quando refreshTokenHash for nulo, vazio ou somente espaços")
        void deveReportarErroRefreshTokenInvalido(String tokenHash) {
            final var ex = createInvalid(OK_USER_ID, tokenHash, OK_EXPIRY);
            assertTrue(messages(ex).stream().anyMatch(m -> m.contains("refreshTokenHash")),
                    "Erro deve mencionar 'refreshTokenHash', mas mensagens foram: " + messages(ex));
        }
    }

    // ─────────────────────────────────────────────────────────
    // expiresAt
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("expiresAt — regras de obrigatoriedade")
    class ExpiresAtObrigatorio {

        @Test
        @DisplayName("deve reportar erro quando expiresAt for nulo")
        void deveReportarErroExpiresAtNulo() {
            final var ex = createInvalid(OK_USER_ID, OK_TOKEN_HASH, null);
            assertTrue(messages(ex).stream().anyMatch(m -> m.contains("expiresAt")),
                    "Erro deve mencionar 'expiresAt', mas mensagens foram: " + messages(ex));
        }
    }

    // ─────────────────────────────────────────────────────────
    // Acúmulo de múltiplos erros
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("acúmulo de erros — múltiplos campos inválidos")
    class AcumuloDeErros {

        @Test
        @DisplayName("deve acumular erros de userId, refreshTokenHash e expiresAt simultaneamente")
        void deveAcumularTresErros() {
            final var ex = createInvalid(null, null, null);
            final var msgs = messages(ex);

            assertTrue(msgs.size() >= 3, "Deve ter ao menos 3 erros, mas teve: " + msgs);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("userId")),          "Faltou erro de userId em: " + msgs);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("refreshTokenHash")), "Faltou erro de refreshTokenHash em: " + msgs);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("expiresAt")),       "Faltou erro de expiresAt em: " + msgs);
        }

        @Test
        @DisplayName("não deve gerar erros de expiresAt quando apenas userId está inválido")
        void naoDeveConfundirCampos() {
            final var ex = createInvalid(null, OK_TOKEN_HASH, OK_EXPIRY);
            final var msgs = messages(ex);

            assertTrue(msgs.stream().anyMatch(m -> m.contains("userId")), "Deve ter erro de userId");
            assertFalse(msgs.stream().anyMatch(m -> m.contains("expiresAt")), "Não deve ter erro de expiresAt (campo válido)");
            assertFalse(msgs.stream().anyMatch(m -> m.contains("refreshTokenHash")), "Não deve ter erro de refreshTokenHash (campo válido)");
        }
    }
}
