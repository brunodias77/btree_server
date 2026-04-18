package com.btree.domain.user.validator;

import com.btree.domain.UnitTest;
import com.btree.domain.user.entity.User;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários focados exclusivamente nas regras de {@link UserValidator}.
 *
 * <p>A estratégia é testar o validator de forma indireta via {@code User.create()},
 * mantendo apenas <b>uma variável por vez</b> fora dos defaults válidos para
 * isolar precisamente qual regra está sendo exercitada.
 *
 * <p>Diferente do {@code UserTest}, que verifica o <em>comportamento do aggregate</em>,
 * este arquivo verifica as <em>mensagens de erro específicas</em> geradas por cada
 * regra do validator: conteúdo da mensagem, quantidade de erros e ausência de
 * falsos positivos.
 */
@DisplayName("Testes do UserValidator acoplado a Entidade User")
public class UserValidatorTest extends UnitTest {

    // ── Constantes ────────────────────────────────────────────

    private static final String OK_USERNAME = "bruno_dias";
    private static final String OK_EMAIL    = "bruno@email.com";
    private static final String OK_HASH     = "Senh@Forte123";

    // ── Helpers ───────────────────────────────────────────────

    /** Executa User.create() e retorna a DomainException; falha o teste se não lançar. */
    private static DomainException createInvalid(String username, String email, String hash) {
        return assertThrows(DomainException.class,
                () -> User.create(username, email, hash, Notification.create()),
                "User.create() deveria ter lançado DomainException");
    }

    /** Verifica se algum dos erros da exception contém a keyword (case-insensitive). */
    private static boolean hasErrorWith(DomainException ex, String keyword) {
        return ex.getErrors().stream()
                .anyMatch(e -> e.message().toLowerCase().contains(keyword.toLowerCase()));
    }

    /** Extrai as mensagens de erro para uso em assertions declarativas. */
    private static List<String> messages(DomainException ex) {
        return ex.getErrors().stream().map(Error::message).toList();
    }

    // ─────────────────────────────────────────────────────────
    // Cenário feliz
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve criar usuário sem erros quando todos os campos são válidos")
    void deveCriarSemErros() {
        final var notification = Notification.create();
        assertDoesNotThrow(() -> User.create(OK_USERNAME, OK_EMAIL, OK_HASH, notification));
        assertFalse(notification.hasError(), "Notification não deve ter erros para entidade válida");
    }

    // ─────────────────────────────────────────────────────────
    // username
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("username — regras de obrigatoriedade")
    class UsernameObrigatorio {

        @ParameterizedTest(name = "username=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("deve reportar erro quando username for nulo, vazio ou somente espaços")
        void deveReportarErroUsernameObrigatorio(String username) {
            final var ex = createInvalid(username, OK_EMAIL, OK_HASH);
            assertTrue(hasErrorWith(ex, "username"),
                    "Erro deve mencionar 'username', mas erros foram: " + messages(ex));
        }
    }

    @Nested
    @DisplayName("username — regras de formato")
    class UsernameFormato {

        @ParameterizedTest(name = "username=[{0}]")
        @ValueSource(strings = {
                "bruno dias",   // espaço
                "bruno!",       // caractere especial
                "br@uno",       // arroba
                "user.name",    // ponto não permitido
                "user name",    // espaço no meio
                " leadingspace",// espaço antes
                "trailingspace "// espaço depois
        })
        @DisplayName("deve reportar erro de formato quando username contiver caracteres inválidos")
        void deveReportarErroFormatoUsername(String username) {
            final var ex = createInvalid(username, OK_EMAIL, OK_HASH);
            assertTrue(hasErrorWith(ex, "username"),
                    "Erro deve mencionar 'username', mas erros foram: " + messages(ex));
        }

        @ParameterizedTest(name = "username=[{0}]")
        @ValueSource(strings = {
                "b",              // 1 char — válido (não há mínimo)
                "bruno",          // simples
                "bruno_dias",     // com underscore
                "bruno-dias",     // com hífen
                "MAIUSCULO",      // só maiúsculas
                "minusculo",      // só minúsculas
                "123456",         // só números
                "Mix_3-Valid",    // combinado
        })
        @DisplayName("deve aceitar username com formatos válidos")
        void deveAceitarUsernameValido(String username) {
            assertDoesNotThrow(() -> User.create(username, OK_EMAIL, OK_HASH, Notification.create()),
                    "Username '" + username + "' deveria ser aceito");
        }

        @Test
        @DisplayName("deve rejeitar username com exatamente 257 caracteres (acima do limite de 256)")
        void deveRejeitarUsernameMaiorQue256() {
            final var username = "a".repeat(257);
            final var ex = createInvalid(username, OK_EMAIL, OK_HASH);
            assertTrue(hasErrorWith(ex, "username"));
        }

        @Test
        @DisplayName("deve aceitar username com exatamente 256 caracteres (no limite)")
        void deveAceitarUsernameLimiteDe256() {
            final var username = "a".repeat(256);
            assertDoesNotThrow(() -> User.create(username, OK_EMAIL, OK_HASH, Notification.create()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // email
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("email — regras de obrigatoriedade")
    class EmailObrigatorio {

        @ParameterizedTest(name = "email=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("deve reportar erro quando email for nulo, vazio ou somente espaços")
        void deveReportarErroEmailObrigatorio(String email) {
            final var ex = createInvalid(OK_USERNAME, email, OK_HASH);
            assertTrue(hasErrorWith(ex, "email"),
                    "Erro deve mencionar 'email', mas erros foram: " + messages(ex));
        }
    }

    @Nested
    @DisplayName("email — regras de formato (RFC simplificada)")
    class EmailFormato {

        @ParameterizedTest(name = "email=[{0}]")
        @ValueSource(strings = {
                "abc",            // sem @
                "abc.com",        // sem @
                "@dominio.com",   // sem local part
                "abc@",           // sem domínio
                "abc@dominio",    // sem TLD (< 2 chars após ponto)
                "abc @mail.com",  // espaço no local part
                "abc@.com",       // ponto logo após @
        })
        @DisplayName("deve reportar erro de formato para emails inválidos")
        void deveReportarErroFormatoEmail(String email) {
            final var ex = createInvalid(OK_USERNAME, email, OK_HASH);
            assertTrue(hasErrorWith(ex, "email"),
                    "Erro deve mencionar 'email' para '" + email + "', mas erros foram: " + messages(ex));
        }

        @ParameterizedTest(name = "email=[{0}]")
        @ValueSource(strings = {
                "bruno@email.com",
                "user@mail.empresa.com.br",
                "test.user+tag@sub.domain.io",
                "a@b.co",
        })
        @DisplayName("deve aceitar emails com formato válido")
        void deveAceitarEmailValido(String email) {
            assertDoesNotThrow(() -> User.create(OK_USERNAME, email, OK_HASH, Notification.create()),
                    "Email '" + email + "' deveria ser aceito");
        }

        @Test
        @DisplayName("deve rejeitar email com mais de 256 caracteres")
        void deveRejeitarEmailLongo() {
            // local part + @ + domínio = 258 chars total
            final var email = "a".repeat(252) + "@x.com";
            assertEquals(258, email.length(), "pré-condição: email tem 258 chars");
            final var ex = createInvalid(OK_USERNAME, email, OK_HASH);
            assertTrue(hasErrorWith(ex, "email"));
        }
    }

    // ─────────────────────────────────────────────────────────
    // password (passwordHash)
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("password — regras de obrigatoriedade")
    class PasswordObrigatorio {

        @ParameterizedTest(name = "password=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("deve reportar erro quando password for nulo, vazio ou somente espaços")
        void deveReportarErroPasswordObrigatorio(String password) {
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, password);
            assertTrue(hasErrorWith(ex, "password"),
                    "Erro deve mencionar 'password', mas erros foram: " + messages(ex));
        }
    }

    @Nested
    @DisplayName("password — regra de comprimento mínimo (8 chars)")
    class PasswordComprimento {

        @ParameterizedTest(name = "password=[{0}]")
        @ValueSource(strings = {
                "Ab1",       // 3 chars
                "Ab1cD",     // 5 chars
                "Ab1cDef",   // 7 chars — ainda 1 char abaixo do mínimo
        })
        @DisplayName("deve reportar erro quando password tiver menos de 8 caracteres")
        void deveRejeitarPasswordCurta(String password) {
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, password);
            assertTrue(hasErrorWith(ex, "password"));
        }

        @Test
        @DisplayName("deve aceitar password com exatamente 8 caracteres (limite mínimo)")
        void deveAceitarPasswordNoLimiteMinimo() {
            assertDoesNotThrow(() -> User.create(OK_USERNAME, OK_EMAIL, "Aa123456", Notification.create()));
        }
    }

    @Nested
    @DisplayName("password — regra de complexidade (maiúscula, minúscula, dígito)")
    class PasswordComplexidade {

        @Test
        @DisplayName("deve reportar erro quando password não contiver letra maiúscula")
        void deveRejeitarSemMaiuscula() {
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, "senh@for1e");
            final var msgs = messages(ex);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("maiúscula")),
                    "Deve mencionar 'maiúscula', mas mensagens foram: " + msgs);
        }

        @Test
        @DisplayName("deve reportar erro quando password não contiver letra minúscula")
        void deveRejeitarSemMinuscula() {
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, "SENH@FORTE1");
            final var msgs = messages(ex);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("minúscula")),
                    "Deve mencionar 'minúscula', mas mensagens foram: " + msgs);
        }

        @Test
        @DisplayName("deve reportar erro quando password não contiver dígito numérico")
        void deveRejeitarSemDigito() {
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, "HashForteABC");
            final var msgs = messages(ex);
            assertTrue(msgs.stream().anyMatch(m -> m.contains("dígito")),
                    "Deve mencionar 'dígito', mas mensagens foram: " + msgs);
        }

        @ParameterizedTest(name = "password=[{0}]")
        @ValueSource(strings = {
                "Aa123456",         // exatamente 8 chars com todos os requisitos
                "Senh@Forte123",    // com caractere especial (extra-forte)
                "SenhaFOrte1000",   // tudo correto, mais longa
                "X1aaaaaaaaaaaaa",  // mínimo com maiúscula no início e dígito
        })
        @DisplayName("deve aceitar passwords que atendem todos os requisitos de complexidade")
        void deveAceitarPasswordComplexa(String password) {
            assertDoesNotThrow(() -> User.create(OK_USERNAME, OK_EMAIL, password, Notification.create()),
                    "Password '" + password + "' deveria ser aceita");
        }
    }

    // ─────────────────────────────────────────────────────────
    // Acúmulo de múltiplos erros
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("acúmulo de erros — múltiplos campos inválidos")
    class AcumuloDeErros {

        @Test
        @DisplayName("deve acumular erros de username, email e password simultaneamente")
        void deveAcumularTresErros() {
            // Todos os campos inválidos: username nulo, email sem @, password sem maiúscula
            final var ex = createInvalid(null, "invalido", "senh@1234");

            final var msgs = messages(ex);
            assertTrue(msgs.size() >= 3, "Deve ter ao menos 3 erros, mas teve: " + msgs);
            assertTrue(hasErrorWith(ex, "username"), "Faltou erro de username em: " + msgs);
            assertTrue(hasErrorWith(ex, "email"),    "Faltou erro de email em: "    + msgs);
            assertTrue(hasErrorWith(ex, "password"), "Faltou erro de password em: " + msgs);
        }

        @Test
        @DisplayName("deve acumular 2 erros de password (sem maiúscula + sem dígito)")
        void deveAcumularErrosDeSenhaSemMaiusculaESemDigito() {
            // "senhaforte" — sem maiúscula (1 erro) e sem dígito (1 erro)
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, "senhaforte");

            final var msgs = messages(ex);
            assertTrue(msgs.size() >= 2, "Deve ter ao menos 2 erros de password, mas teve: " + msgs);

            final long passwordErrors = msgs.stream()
                    .filter(m -> m.toLowerCase().contains("password")).count();
            assertTrue(passwordErrors >= 2,
                    "Deve ter ao menos 2 erros de password, mas erros de password foram: " + passwordErrors);
        }

        @Test
        @DisplayName("deve acumular todos os erros de password de uma vez (sem maiúscula, sem minúscula e sem dígito)")
        void deveAcumularTodosErrosDeSenha() {
            // 8 chars, sem maiúscula, sem minúscula, sem dígito — 3 erros de complexidade
            // Obs.: só caracteres especiais, mas o validator não tem regra para isso, logo:
            // sem uppercase (1), sem lowercase (1), sem dígito (1) = 3 erros
            final var ex = createInvalid(OK_USERNAME, OK_EMAIL, "@@@@@@@@");

            final var msgs = messages(ex);
            // Sem maiúscula, sem minúscula, sem dígito = mínimo 3 erros de password
            assertTrue(msgs.size() >= 3, "Deve ter ao menos 3 erros, mas teve: " + msgs);
        }

        @Test
        @DisplayName("não deve gerar erros de email quando o username é que está inválido")
        void naoDeveConfundirCampos() {
            // Apenas username inválido — email e password são válidos
            final var ex = createInvalid("user name!", OK_EMAIL, OK_HASH);

            assertTrue(hasErrorWith(ex, "username"), "Deve ter erro de username");
            assertFalse(hasErrorWith(ex, "email"),    "Não deve ter erro de email (email é válido)");
            assertFalse(hasErrorWith(ex, "password"), "Não deve ter erro de password (password é válida)");
        }
    }
}
