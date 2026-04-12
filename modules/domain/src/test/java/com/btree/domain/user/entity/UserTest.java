package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.event.UserCreatedEvent;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários do aggregate root {@link User}.
 *
 * <p>Cobre todos os comportamentos expostos publicamente:
 * <ul>
 *   <li>{@code create()} — factory de novo usuário (validação + event sourcing)</li>
 *   <li>{@code with()} — reconstituição sem validação nem eventos</li>
 *   <li>{@code validate()} — regras do {@code UserValidator} via {@code Notification}</li>
 *   <li>{@code addRole()} — gerenciamento do Set de roles</li>
 * </ul>
 */
@DisplayName("User (Aggregate Root)")
public class UserTest extends UnitTest {

    // ── Constantes de teste ───────────────────────────────────

    private static final String VALID_USERNAME = "bruno_dias";
    private static final String VALID_EMAIL    = "bruno@email.com";
    private static final String VALID_HASH     = "Hashforte123";

    // ── Helpers ───────────────────────────────────────────────

    /** Cria um usuário válido para testes que não focam em criação. */
    private static User validUser() {
        return User.create(VALID_USERNAME, VALID_EMAIL, VALID_HASH, Notification.create());
    }

    /** Helper de factory que usa os defaults válidos com overrides específicos. */
    private static DomainException expectCreateFailure(String username, String email, String hash) {
        return assertThrows(DomainException.class,
                () -> User.create(username, email, hash, Notification.create()));
    }

    // ─────────────────────────────────────────────────────────
    // create()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — campos e defaults")
    class CreateDefaults {

        @Test
        @DisplayName("deve preencher username, email e passwordHash com os valores fornecidos")
        void devePersistirCamposFornecidos() {
            final var user = validUser();

            assertEquals(VALID_USERNAME, user.getUsername());
            assertEquals(VALID_EMAIL,    user.getEmail());
            assertEquals(VALID_HASH,     user.getPasswordHash());
        }

        @Test
        @DisplayName("deve gerar um UserId único e não-nulo")
        void deveGerarIdNaoNulo() {
            final var user = validUser();
            assertNotNull(user.getId());
            assertNotNull(user.getId().getValue());
        }

        @Test
        @DisplayName("IDs de dois usuários criados sequencialmente devem ser diferentes")
        void deveGerarIdsUnicos() {
            final var a = validUser();
            final var b = User.create("outro", "outro@email.com", VALID_HASH, Notification.create());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve inicializar todos os flags de segurança com valores seguros")
        void deveInicializarFlagsSeguranca() {
            final var user = validUser();

            assertFalse(user.isEmailVerified(),      "emailVerified deve ser false");
            assertFalse(user.isPhoneNumberVerified(), "phoneNumberVerified deve ser false");
            assertFalse(user.isTwoFactorEnabled(),    "twoFactorEnabled deve ser false");
            assertFalse(user.isAccountLocked(),       "accountLocked deve ser false");
            assertTrue(user.isEnabled(),              "enabled deve ser true");
        }

        @Test
        @DisplayName("deve inicializar campos opcionais de segurança como nulos/zero")
        void deveInicializarCamposOpcionaisNulos() {
            final var user = validUser();

            assertNull(user.getPhoneNumber());
            assertNull(user.getTwoFactorSecret());
            assertNull(user.getLockExpiresAt());
            assertEquals(0, user.getAccessFailedCount());
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt no momento da criação")
        void devePreencherTimestamps() {
            final var before = Instant.now();
            final var user   = validUser();
            final var after  = Instant.now();

            assertNotNull(user.getCreatedAt());
            assertNotNull(user.getUpdatedAt());
            assertFalse(user.getCreatedAt().isBefore(before));
            assertFalse(user.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("deve criar Profile filho associado ao ID do usuário")
        void deveCriarProfileFilho() {
            final var user    = validUser();
            final var profile = user.getProfile();

            assertNotNull(profile,            "profile não deve ser nulo");
            assertNotNull(profile.getId());
            assertEquals(user.getId(), profile.getUserId());
            assertEquals("pt-BR",      profile.getPreferredLanguage(), "idioma padrão");
            assertEquals("BRL",        profile.getPreferredCurrency(), "moeda padrão");
            assertNull(profile.getFirstName());
            assertNull(profile.getLastName());
        }

        @Test
        @DisplayName("deve criar NotificationPreference filho com os defaults do schema")
        void deveCriarNotificationPreferenceFilha() {
            final var user = validUser();
            final var pref = user.getNotificationPreference();

            assertNotNull(pref,          "notificationPreference não deve ser nulo");
            assertNotNull(pref.getId());
            assertEquals(user.getId(), pref.getUserId());

            // Canais: email=true, push=true, sms=false
            assertTrue(pref.isEmailEnabled(),  "email notifications deve estar ativo");
            assertTrue(pref.isPushEnabled(),   "push notifications deve estar ativo");
            assertFalse(pref.isSmsEnabled(),   "sms deve estar desativado por padrão");

            // Tipos: orderUpdates=true, promotions=true, priceDrops=true, backInStock=true, newsletter=false
            assertTrue(pref.isOrderUpdates());
            assertTrue(pref.isPromotions());
            assertTrue(pref.isPriceDrops());
            assertTrue(pref.isBackInStock());
            assertFalse(pref.isNewsletter(), "newsletter deve estar desativada por padrão");
        }
    }

    // ─────────────────────────────────────────────────────────
    // create() — UserCreatedEvent
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — UserCreatedEvent")
    class CreateEvent {

        @Test
        @DisplayName("deve registrar exatamente um UserCreatedEvent")
        void deveRegistrarUmEvento() {
            final var user = validUser();
            assertEquals(1, user.getDomainEvents().size());
        }

        @Test
        @DisplayName("o evento deve referenciar o ID, username e email do usuário criado")
        void eventoDeveConterDadosDoUsuario() {
            final var user  = validUser();
            final var event = (UserCreatedEvent) user.getDomainEvents().get(0);

            assertEquals(user.getId().getValue().toString(), event.getUserId());
            assertEquals(VALID_USERNAME,                     event.getUsername());
            assertEquals(VALID_EMAIL,                        event.getEmail());
        }

        @Test
        @DisplayName("o evento deve ter aggregateType='User' e eventType='user.created'")
        void eventoDeveConterMetadados() {
            final var event = (UserCreatedEvent) validUser().getDomainEvents().get(0);

            assertEquals("User",          event.getAggregateType());
            assertEquals("user.created",  event.getEventType());
        }

        @Test
        @DisplayName("o evento deve ter eventId e occurredOn preenchidos")
        void eventoDeveConterAuditoria() {
            final var event = (UserCreatedEvent) validUser().getDomainEvents().get(0);

            assertNotNull(event.getEventId());
            assertNotNull(event.getOccurredOn());
        }

        @Test
        @DisplayName("o aggregateId do evento deve ser igual ao subject do token JWT (userId string)")
        void eventoAggregateIdDeveSerUserId() {
            final var user  = validUser();
            final var event = (UserCreatedEvent) user.getDomainEvents().get(0);

            // aggregateId é usado como "sub" no JWT e como chave de roteamento do outbox
            assertEquals(user.getId().getValue().toString(), event.getAggregateId());
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate() — username
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — username")
    class UsernameValidation {

        @Test
        @DisplayName("deve rejeitar username nulo")
        void deveRejeitarNulo() {
            final var ex = expectCreateFailure(null, VALID_EMAIL, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "username"), "erro deve mencionar 'username'");
        }

        @Test
        @DisplayName("deve rejeitar username vazio ou somente espaços")
        void deveRejeitarVazio() {
            final var ex = expectCreateFailure("   ", VALID_EMAIL, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "username"));
        }

        @Test
        @DisplayName("deve rejeitar username com caracteres inválidos (espaço)")
        void deveRejeitarComEspaco() {
            final var ex = expectCreateFailure("bruno dias", VALID_EMAIL, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "username"));
        }

        @Test
        @DisplayName("deve rejeitar username com caracteres inválidos (especiais)")
        void deveRejeitarComEspeciais() {
            final var ex = expectCreateFailure("bruno!", VALID_EMAIL, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "username"));
        }

        @Test
        @DisplayName("deve rejeitar username com mais de 256 caracteres")
        void deveRejeitarUsernameAcimaDoLimite() {
            final var ex = expectCreateFailure("a".repeat(257), VALID_EMAIL, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "username"));
        }

        @Test
        @DisplayName("deve aceitar username com exatamente 256 caracteres (limite)")
        void deveAceitarUsernameLimiteExato() {
            // 256 chars com apenas alfanuméricos — válido segundo o padrão do validator
            final var username = "a".repeat(256);
            assertDoesNotThrow(() -> User.create(username, VALID_EMAIL, VALID_HASH, Notification.create()));
        }

        @Test
        @DisplayName("deve aceitar username com hífen e underscore")
        void deveAceitarHifenEUnderscore() {
            final var user = User.create("bruno-dias_99", VALID_EMAIL, VALID_HASH, Notification.create());
            assertEquals("bruno-dias_99", user.getUsername());
        }

        @Test
        @DisplayName("deve aceitar username apenas com números")
        void deveAceitarSomenteNumeros() {
            assertDoesNotThrow(() -> User.create("12345", VALID_EMAIL, VALID_HASH, Notification.create()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate() — email
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — email")
    class EmailValidation {

        @Test
        @DisplayName("deve rejeitar email nulo")
        void deveRejeitarNulo() {
            final var ex = expectCreateFailure(VALID_USERNAME, null, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve rejeitar email vazio")
        void deveRejeitarVazio() {
            final var ex = expectCreateFailure(VALID_USERNAME, "", VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve rejeitar email sem @")
        void deveRejeitarSemArroba() {
            final var ex = expectCreateFailure(VALID_USERNAME, "invalido", VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve rejeitar email sem domínio após @")
        void deveRejeitarSemDominio() {
            final var ex = expectCreateFailure(VALID_USERNAME, "user@", VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve rejeitar email sem TLD (ex: user@domain)")
        void deveRejeitarSemTld() {
            final var ex = expectCreateFailure(VALID_USERNAME, "user@domain", VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve rejeitar email com mais de 256 caracteres")
        void deveRejeitarEmailAcimaDoLimite() {
            final var email = "a".repeat(252) + "@x.com"; // 258 chars, acima do limite de 256
            final var ex = expectCreateFailure(VALID_USERNAME, email, VALID_HASH);
            assertTrue(hasErrorAbout(ex, "email"));
        }

        @Test
        @DisplayName("deve aceitar email com subdomain")
        void deveAceitarEmailComSubdomain() {
            assertDoesNotThrow(() ->
                    User.create(VALID_USERNAME, "user@mail.empresa.com.br", VALID_HASH, Notification.create()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate() — passwordHash
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — password")
    class PasswordValidation {

        @Test
        @DisplayName("deve rejeitar password nulo")
        void deveRejeitarNulo() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, null);
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve rejeitar password vazio")
        void deveRejeitarVazio() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "");
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve rejeitar password menor que 8 caracteres")
        void deveRejeitarCurto() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "Ab1");
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve rejeitar password sem letra maiúscula")
        void deveRejeitarSemMaiuscula() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "hashforte123");
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve rejeitar password sem letra minúscula")
        void deveRejeitarSemMinuscula() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "HASHFORTE123");
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve rejeitar password sem dígito numérico")
        void deveRejeitarSemDigito() {
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "HashForteABC");
            assertTrue(hasErrorAbout(ex, "password"));
        }

        @Test
        @DisplayName("deve aceitar password com exatamente 8 caracteres válidos (limite mínimo)")
        void deveAceitarPasswordNoLimiteMinimo() {
            assertDoesNotThrow(() ->
                    User.create(VALID_USERNAME, VALID_EMAIL, "Aa123456", Notification.create()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate() — múltiplos erros acumulados
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — acúmulo de múltiplos erros")
    class MultipleErrors {

        @Test
        @DisplayName("deve acumular erros de username e email simultaneamente")
        void deveAcumularErrosDeUsernameEEmail() {
            final var ex = expectCreateFailure(null, "invalido", VALID_HASH);

            final var errors = ex.getErrors();
            assertTrue(errors.size() >= 2, "deve ter ao menos 2 erros");
            assertTrue(hasErrorAbout(ex, "username"), "deve ter erro de username");
            assertTrue(hasErrorAbout(ex, "email"),    "deve ter erro de email");
        }

        @Test
        @DisplayName("deve acumular erros de password (sem maiúscula, sem dígito)")
        void deveAcumularErrosDeSenha() {
            // "senhafraca" — sem maiúscula e sem dígito: 2 erros esperados
            final var ex = expectCreateFailure(VALID_USERNAME, VALID_EMAIL, "senhafraca");
            assertTrue(ex.getErrors().size() >= 2, "deve ter ao menos 2 erros de password");
        }
    }

    // ─────────────────────────────────────────────────────────
    // addRole()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addRole()")
    class AddRole {

        @Test
        @DisplayName("deve adicionar uma role válida")
        void deveAdicionarRole() {
            final var user = validUser();
            user.addRole("ADMIN");
            assertTrue(user.getRoles().contains("ADMIN"));
        }

        @Test
        @DisplayName("não deve duplicar role já existente (comportamento de Set)")
        void naoDeveDuplicarRole() {
            final var user = validUser();
            user.addRole("USER");
            user.addRole("USER");
            assertEquals(1, user.getRoles().size());
        }

        @Test
        @DisplayName("não deve adicionar role nula")
        void naoDeveAdicionarRoleNula() {
            final var user = validUser();
            user.addRole(null);
            assertTrue(user.getRoles().isEmpty());
        }

        @Test
        @DisplayName("não deve adicionar role em branco")
        void naoDeveAdicionarRoleEmBranco() {
            final var user = validUser();
            user.addRole("   ");
            assertTrue(user.getRoles().isEmpty());
        }

        @Test
        @DisplayName("deve suportar múltiplas roles distintas")
        void deveSuportarMultiplasRoles() {
            final var user = validUser();
            user.addRole("USER");
            user.addRole("ADMIN");
            user.addRole("MANAGER");
            assertEquals(3, user.getRoles().size());
        }

        @Test
        @DisplayName("roles devem ser case-sensitive (USER ≠ user)")
        void rolesDevemSerCaseSensitive() {
            final var user = validUser();
            user.addRole("USER");
            user.addRole("user");
            assertEquals(2, user.getRoles().size(), "USER e user devem ser roles distintas");
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos de um usuário ativo sem 2FA")
        void deveReconstituirUsuarioAtivo() {
            final var id      = UserId.unique();
            final var now     = Instant.now();
            final var profile = Profile.create(id);
            final var pref    = NotificationPreference.create(id);

            final var user = User.with(
                    id, "recon", "recon@email.com",
                    true, "SomeHash1",
                    "+5511999999999", true,
                    false, null,
                    false, null, 0,
                    true, now, now, 1,
                    profile, pref
            );

            assertEquals(id,               user.getId());
            assertEquals("recon",          user.getUsername());
            assertEquals("recon@email.com",user.getEmail());
            assertTrue(user.isEmailVerified());
            assertEquals("SomeHash1",      user.getPasswordHash());
            assertEquals("+5511999999999", user.getPhoneNumber());
            assertTrue(user.isPhoneNumberVerified());
            assertFalse(user.isTwoFactorEnabled());
            assertFalse(user.isAccountLocked());
            assertTrue(user.isEnabled());
            assertSame(profile, user.getProfile());
            assertSame(pref,    user.getNotificationPreference());
        }

        @Test
        @DisplayName("deve reconstituir usuário com 2FA ativo e secret preenchido")
        void deveReconstituirUsuarioCom2FA() {
            final var id  = UserId.unique();
            final var now = Instant.now();

            final var user = User.with(
                    id, "totp_user", "totp@email.com",
                    true, "SomeHash1",
                    null, false,
                    true, "JBSWY3DPEHPK3PXP",
                    false, null, 0,
                    true, now, now, 1,
                    Profile.create(id), NotificationPreference.create(id)
            );

            assertTrue(user.isTwoFactorEnabled());
            assertEquals("JBSWY3DPEHPK3PXP", user.getTwoFactorSecret());
        }

        @Test
        @DisplayName("deve reconstituir usuário com conta bloqueada e lockExpiresAt preenchido")
        void deveReconstituirUsuarioBloqueado() {
            final var id         = UserId.unique();
            final var now        = Instant.now();
            final var lockExpiry = now.plusSeconds(3600);

            final var user = User.with(
                    id, "locked_user", "locked@email.com",
                    false, "SomeHash1",
                    null, false,
                    false, null,
                    true, lockExpiry, 5,
                    true, now, now, 2,
                    Profile.create(id), NotificationPreference.create(id)
            );

            assertTrue(user.isAccountLocked());
            assertEquals(lockExpiry, user.getLockExpiresAt());
            assertEquals(5,          user.getAccessFailedCount());
        }

        @Test
        @DisplayName("with() não deve gerar nenhum domain event")
        void naoDeveGerarEventos() {
            final var id  = UserId.unique();
            final var now = Instant.now();

            final var user = User.with(
                    id, "recon", "recon@email.com",
                    false, "SomeHash1",
                    null, false,
                    false, null,
                    false, null, 0,
                    true, now, now, 0,
                    Profile.create(id), NotificationPreference.create(id)
            );

            assertTrue(user.getDomainEvents().isEmpty(),
                    "with() é reconstituição — não deve produzir domain events");
        }

        @Test
        @DisplayName("with() não deve lançar exceção mesmo com dados que seriam inválidos no create()")
        void naoDeveLancarExcecaoComDadosInvalidos() {
            // O with() não executa o validator — confia que os dados do banco estão corretos
            final var id  = UserId.unique();
            final var now = Instant.now();

            assertDoesNotThrow(() -> User.with(
                    id, "", "",   // username e email vazios não lançam exceção no with()
                    false, null,
                    null, false,
                    false, null,
                    false, null, 0,
                    false, now, now, 0,
                    Profile.create(id), NotificationPreference.create(id)
            ));
        }
    }

    // ── Utilitário ────────────────────────────────────────────

    /** Verifica se algum erro na DomainException menciona a keyword (case-insensitive). */
    private static boolean hasErrorAbout(final DomainException ex, final String keyword) {
        return ex.getErrors().stream()
                .anyMatch(e -> e.message().toLowerCase().contains(keyword.toLowerCase()));
    }
}
