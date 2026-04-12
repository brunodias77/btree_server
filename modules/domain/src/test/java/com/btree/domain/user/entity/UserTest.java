package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.event.UserCreatedEvent;
import com.btree.domain.user.identifier.NotificationPreferenceId;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User (Aggregate Root)")
public class UserTest extends UnitTest {

    // ── Helpers ───────────────────────────────────────────────

    private static User validUser() {
        return User.create("bruno_dias", "bruno@email.com", "Hashforte123", Notification.create());
    }

    // ── Creation ──────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar usuário com todos os campos padrão corretos")
        void deveCriarComCamposPadrao() {
            final var notification = Notification.create();

            final var user = User.create("bruno_dias", "bruno@email.com", "Hashforte123", notification);

            assertFalse(notification.hasError());
            assertNotNull(user);
            assertNotNull(user.getId());
            assertEquals("bruno_dias", user.getUsername());
            assertEquals("bruno@email.com", user.getEmail());
            assertEquals("Hashforte123", user.getPasswordHash());

            // Defaults de segurança
            assertFalse(user.isEmailVerified(),       "e-mail não deve estar verificado ao criar");
            assertFalse(user.isPhoneNumberVerified(),  "telefone não deve estar verificado ao criar");
            assertFalse(user.isTwoFactorEnabled(),     "2FA não deve estar ativo ao criar");
            assertFalse(user.isAccountLocked(),        "conta não deve estar bloqueada ao criar");
            assertTrue(user.isEnabled(),               "conta deve estar habilitada ao criar");
            assertNull(user.getPhoneNumber());
            assertNull(user.getTwoFactorSecret());
            assertNull(user.getLockExpiresAt());
            assertEquals(0, user.getAccessFailedCount());
            assertNotNull(user.getCreatedAt());
            assertNotNull(user.getUpdatedAt());
        }

        @Test
        @DisplayName("deve criar Profile filho com defaults corretos")
        void deveCriarProfileComDefaults() {
            final var user = validUser();
            final var profile = user.getProfile();

            assertNotNull(profile);
            assertNotNull(profile.getId());
            assertEquals(user.getId(), profile.getUserId());

            // Campos livres na criação
            assertNull(profile.getFirstName());
            assertNull(profile.getLastName());
            assertNull(profile.getDisplayName());
            assertNull(profile.getAvatarUrl());
            assertNull(profile.getBirthDate());
            assertNull(profile.getGender());
            assertNull(profile.getCpf());
            assertNull(profile.getDeletedAt());

            // Defaults de idioma/moeda
            assertEquals("pt-BR", profile.getPreferredLanguage());
            assertEquals("BRL",   profile.getPreferredCurrency());
            assertFalse(profile.isNewsletterSubscribed());
        }

        @Test
        @DisplayName("deve criar NotificationPreference filho com defaults corretos")
        void deveCriarNotificationPreferenceComDefaults() {
            final var user = validUser();
            final var pref = user.getNotificationPreference();

            assertNotNull(pref);
            assertNotNull(pref.getId());
            assertEquals(user.getId(), pref.getUserId());

            // Canais habilitados por padrão
            assertTrue(pref.isEmailEnabled());
            assertTrue(pref.isPushEnabled());
            assertFalse(pref.isSmsEnabled());  // SMS desativado por padrão

            // Tipos de notificação habilitados por padrão
            assertTrue(pref.isOrderUpdates());
            assertTrue(pref.isPromotions());
            assertTrue(pref.isPriceDrops());
            assertTrue(pref.isBackInStock());
            assertFalse(pref.isNewsletter());  // newsletter desativada por padrão
        }

        @Test
        @DisplayName("deve registrar UserCreatedEvent com dados corretos")
        void deveRegistrarUserCreatedEvent() {
            final var user = validUser();
            final var events = user.getDomainEvents();

            assertEquals(1, events.size(), "deve haver exatamente 1 evento após criação");

            final var event = (UserCreatedEvent) events.get(0);
            assertEquals(user.getId().getValue().toString(), event.getUserId());
            assertEquals("bruno_dias",       event.getUsername());
            assertEquals("bruno@email.com",  event.getEmail());
            assertEquals("User",             event.getAggregateType());
            assertEquals("user.created",     event.getEventType());
            assertNotNull(event.getEventId());
            assertNotNull(event.getOccurredOn());
        }

        @Test
        @DisplayName("cada create deve gerar um ID único")
        void deveGerarIdUnico() {
            final var userA = validUser();
            final var userB = User.create("outro", "outro@email.com", "Hashforte123", Notification.create());
            assertNotEquals(userA.getId(), userB.getId());
        }
    }

    // ── Validation — username ─────────────────────────────────

    @Nested
    @DisplayName("validação de username")
    class UsernameValidation {

        @Test
        @DisplayName("deve lançar DomainException quando username for nulo")
        void deveRejeitarUsernameNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create(null, "bruno@email.com", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("username")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando username for vazio")
        void deveRejeitarUsernameVazio() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("  ", "bruno@email.com", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("username")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando username contiver caracteres inválidos")
        void deveRejeitarUsernameComCaracteresInvalidos() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno dias!", "bruno@email.com", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("username")));
        }

        @Test
        @DisplayName("deve aceitar username com hífens e underlines")
        void deveAceitarUsernameComHifenEUnderscore() {
            final var notification = Notification.create();
            final var user = User.create("bruno-dias_99", "bruno@email.com", "Hashforte123", notification);
            assertFalse(notification.hasError());
            assertEquals("bruno-dias_99", user.getUsername());
        }
    }

    // ── Validation — email ────────────────────────────────────

    @Nested
    @DisplayName("validação de email")
    class EmailValidation {

        @Test
        @DisplayName("deve lançar DomainException quando email for nulo")
        void deveRejeitarEmailNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", null, "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("email")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando email for vazio")
        void deveRejeitarEmailVazio() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("email")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando email não tiver @")
        void deveRejeitarEmailSemArroba() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "emailinvalido", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("email")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando email não tiver domínio válido")
        void deveRejeitarEmailSemDominio() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "email@", "Hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("email")));
        }
    }

    // ── Validation — password ─────────────────────────────────

    @Nested
    @DisplayName("validação de password")
    class PasswordValidation {

        @Test
        @DisplayName("deve lançar DomainException quando password for nulo")
        void deveRejeitarPasswordNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", null, Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando password for vazio")
        void deveRejeitarPasswordVazio() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", "", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando password for menor que 8 caracteres")
        void deveRejeitarPasswordCurto() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", "Ab1", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando password não tiver letra maiúscula")
        void deveRejeitarPasswordSemMaiuscula() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", "hashforte123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando password não tiver letra minúscula")
        void deveRejeitarPasswordSemMinuscula() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", "HASHFORTE123", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando password não tiver dígito numérico")
        void deveRejeitarPasswordSemDigito() {
            final var ex = assertThrows(DomainException.class,
                    () -> User.create("bruno_dias", "bruno@email.com", "HashForteABC", Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("password")));
        }
    }

    // ── Behaviors ─────────────────────────────────────────────

    @Nested
    @DisplayName("addRole()")
    class AddRole {

        @Test
        @DisplayName("deve adicionar role não duplicada")
        void deveAdicionarRole() {
            final var user = validUser();
            user.addRole("ADMIN");
            assertTrue(user.getRoles().contains("ADMIN"));
        }

        @Test
        @DisplayName("não deve adicionar role duplicada (Set)")
        void naoDeveAdicionarRoleDuplicada() {
            final var user = validUser();
            user.addRole("USER");
            user.addRole("USER");
            assertEquals(1, user.getRoles().size());
        }

        @Test
        @DisplayName("não deve adicionar role nula ou em branco")
        void naoDeveAdicionarRoleNulaOuBranco() {
            final var user = validUser();
            user.addRole(null);
            user.addRole("  ");
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
    }

    // ── Factory with() — reconstituição ───────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir User com todos os campos fornecidos")
        void deveReconstituirUser() {
            final var id      = UserId.unique();
            final var now     = Instant.now();
            final var profile = Profile.create(id);
            final var pref    = NotificationPreference.create(id);

            final var user = User.with(
                    id, "reconstituted", "recon@email.com",
                    true, "SomeHash1",
                    "+5511999999999", true,
                    false, null,
                    false, null, 0,
                    true, now, now, 1,
                    profile, pref
            );

            assertEquals(id,                    user.getId());
            assertEquals("reconstituted",       user.getUsername());
            assertEquals("recon@email.com",     user.getEmail());
            assertTrue(user.isEmailVerified());
            assertEquals("SomeHash1",           user.getPasswordHash());
            assertEquals("+5511999999999",      user.getPhoneNumber());
            assertTrue(user.isPhoneNumberVerified());
            assertFalse(user.isTwoFactorEnabled());
            assertFalse(user.isAccountLocked());
            assertTrue(user.isEnabled());
            assertSame(profile, user.getProfile());
            assertSame(pref,    user.getNotificationPreference());
            // A reconstituição NÃO gera eventos
            assertTrue(user.getDomainEvents().isEmpty(), "with() não deve gerar domain events");
        }
    }
}
