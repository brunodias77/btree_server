package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Profile (Entity)")
public class ProfileTest extends UnitTest {

    // ── Helpers ───────────────────────────────────────────────

    private static UserId anyUserId() {
        return UserId.unique();
    }

    private static Profile newProfile() {
        return Profile.create(anyUserId());
    }

    // ── create() ──────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class CreateFactory {

        @Test
        @DisplayName("deve gerar ID único a cada chamada")
        void deveGerarIdUnico() {
            final var userId = anyUserId();
            final var a = Profile.create(userId);
            final var b = Profile.create(userId);
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve associar o userId corretamente")
        void deveAssociarUserId() {
            final var userId  = anyUserId();
            final var profile = Profile.create(userId);
            assertEquals(userId, profile.getUserId());
        }

        @Test
        @DisplayName("deve inicializar campos opcionais como nulos")
        void deveTerCamposOpcionaisNulos() {
            final var profile = newProfile();
            assertNull(profile.getFirstName());
            assertNull(profile.getLastName());
            assertNull(profile.getDisplayName());
            assertNull(profile.getAvatarUrl());
            assertNull(profile.getBirthDate());
            assertNull(profile.getGender());
            assertNull(profile.getCpf());
            assertNull(profile.getAcceptedTermsAt());
            assertNull(profile.getAcceptedPrivacyAt());
            assertNull(profile.getDeletedAt());
        }

        @Test
        @DisplayName("deve inicializar com idioma pt-BR, moeda BRL e newsletter desativada")
        void deveTerDefaults() {
            final var profile = newProfile();
            assertEquals("pt-BR", profile.getPreferredLanguage());
            assertEquals("BRL",   profile.getPreferredCurrency());
            assertFalse(profile.isNewsletterSubscribed());
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt na criação")
        void deveTerTimestamps() {
            final var profile = newProfile();
            assertNotNull(profile.getCreatedAt());
            assertNotNull(profile.getUpdatedAt());
        }
    }

    // ── with() — reconstituição ───────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos fornecidos corretamente")
        void deveReconstituirTodosCampos() {
            final var userId    = anyUserId();
            final var profileId = ProfileId.unique();
            final var now       = Instant.now();
            final var birthDate = LocalDate.of(1990, 5, 20);

            final var profile = Profile.with(
                    profileId, userId,
                    "Bruno", "Dias", "Bruno Dias",
                    "https://cdn.example.com/avatar.jpg",
                    birthDate, "M", "529.982.247-25",
                    "en-US", "USD", true,
                    now, now,
                    now, now, null
            );

            assertEquals(profileId,                              profile.getId());
            assertEquals(userId,                                 profile.getUserId());
            assertEquals("Bruno",                               profile.getFirstName());
            assertEquals("Dias",                                profile.getLastName());
            assertEquals("Bruno Dias",                          profile.getDisplayName());
            assertEquals("https://cdn.example.com/avatar.jpg",  profile.getAvatarUrl());
            assertEquals(birthDate,                             profile.getBirthDate());
            assertEquals("M",                                   profile.getGender());
            assertEquals("529.982.247-25",                      profile.getCpf());
            assertEquals("en-US",                               profile.getPreferredLanguage());
            assertEquals("USD",                                  profile.getPreferredCurrency());
            assertTrue(profile.isNewsletterSubscribed());
            assertNotNull(profile.getAcceptedTermsAt());
            assertNotNull(profile.getAcceptedPrivacyAt());
            assertNull(profile.getDeletedAt());
        }
    }

    // ── update() ──────────────────────────────────────────────

    @Nested
    @DisplayName("update() — atualização básica")
    class BasicUpdate {

        @Test
        @DisplayName("deve atualizar firstName, lastName e avatarUrl")
        void deveAtualizarCamposBasicos() {
            final var profile = newProfile();
            profile.update("Bruno", "Dias", "https://cdn.example.com/avatar.jpg");

            assertEquals("Bruno",                              profile.getFirstName());
            assertEquals("Dias",                               profile.getLastName());
            assertEquals("https://cdn.example.com/avatar.jpg", profile.getAvatarUrl());
        }

        @Test
        @DisplayName("deve atualizar updatedAt ao chamar update()")
        void deveAtualizarUpdatedAt() throws InterruptedException {
            final var profile = newProfile();
            final var before  = profile.getUpdatedAt();
            Thread.sleep(5);
            profile.update("Bruno", "Dias", null);
            assertTrue(profile.getUpdatedAt().isAfter(before));
        }

        @Test
        @DisplayName("deve aceitar campos nulos em update() — limpa os campos existentes")
        void deveAceitarNulos() {
            final var profile = newProfile();
            profile.update("Bruno", "Dias", "https://url.com");
            profile.update(null, null, null);
            assertNull(profile.getFirstName());
            assertNull(profile.getLastName());
            assertNull(profile.getAvatarUrl());
        }
    }

    // ── updatePersonalData() ──────────────────────────────────

    @Nested
    @DisplayName("updatePersonalData() — atualização de dados pessoais")
    class PersonalDataUpdate {

        @Test
        @DisplayName("deve atualizar todos os campos pessoais")
        void deveAtualizarTodosCampos() {
            final var profile   = newProfile();
            final var birthDate = LocalDate.of(1990, 5, 20);

            profile.updatePersonalData(
                    "Bruno", "Dias",
                    "529.982.247-25", birthDate,
                    "M", "en-US", "USD", true
            );

            assertEquals("Bruno",          profile.getFirstName());
            assertEquals("Dias",           profile.getLastName());
            assertEquals("Bruno Dias",     profile.getDisplayName());
            assertEquals("529.982.247-25", profile.getCpf());
            assertEquals(birthDate,        profile.getBirthDate());
            assertEquals("M",              profile.getGender());
            assertEquals("en-US",          profile.getPreferredLanguage());
            assertEquals("USD",            profile.getPreferredCurrency());
            assertTrue(profile.isNewsletterSubscribed());
        }

        @Test
        @DisplayName("deve construir displayName com firstName e lastName")
        void deveConstruirDisplayNameCompleto() {
            final var profile = newProfile();
            profile.updatePersonalData("Ana", "Lima", null, null, null, null, null, false);
            assertEquals("Ana Lima", profile.getDisplayName());
        }

        @Test
        @DisplayName("deve construir displayName apenas com firstName quando lastName for nulo")
        void deveConstruirDisplayNameSoComFirstName() {
            final var profile = newProfile();
            profile.updatePersonalData("Ana", null, null, null, null, null, null, false);
            assertEquals("Ana", profile.getDisplayName());
        }

        @Test
        @DisplayName("deve construir displayName apenas com lastName quando firstName for nulo")
        void deveConstruirDisplayNameSoComLastName() {
            final var profile = newProfile();
            profile.updatePersonalData(null, "Lima", null, null, null, null, null, false);
            assertEquals("Lima", profile.getDisplayName());
        }

        @Test
        @DisplayName("deve usar 'pt-BR' como fallback quando preferredLanguage for nulo")
        void deveFallbackLanguage() {
            final var profile = newProfile();
            profile.updatePersonalData(null, null, null, null, null, null, null, false);
            assertEquals("pt-BR", profile.getPreferredLanguage());
        }

        @Test
        @DisplayName("deve usar 'BRL' como fallback quando preferredCurrency for nulo")
        void deveFallbackCurrency() {
            final var profile = newProfile();
            profile.updatePersonalData(null, null, null, null, null, null, null, false);
            assertEquals("BRL", profile.getPreferredCurrency());
        }

        @Test
        @DisplayName("deve atualizar updatedAt ao chamar updatePersonalData()")
        void deveAtualizarUpdatedAt() throws InterruptedException {
            final var profile = newProfile();
            final var before  = profile.getUpdatedAt();
            Thread.sleep(5);
            profile.updatePersonalData("X", "Y", null, null, null, null, null, false);
            assertTrue(profile.getUpdatedAt().isAfter(before));
        }
    }

    // ── softDelete() ──────────────────────────────────────────

    @Nested
    @DisplayName("softDelete()")
    class SoftDelete {

        @Test
        @DisplayName("deve preencher deletedAt ao chamar softDelete()")
        void devePrencherDeletedAt() {
            final var profile = newProfile();
            assertNull(profile.getDeletedAt(), "deletedAt deve ser nulo antes do soft delete");
            profile.softDelete();
            assertNotNull(profile.getDeletedAt(), "deletedAt deve estar preenchido após o soft delete");
        }

        @Test
        @DisplayName("deletedAt deve ser um instante posterior à criação do profile")
        void deletedAtDeveSerPosteriorAoCriadoAt() throws InterruptedException {
            final var profile = newProfile();
            Thread.sleep(5);
            profile.softDelete();
            assertTrue(profile.getDeletedAt().isAfter(profile.getCreatedAt()));
        }
    }

    // ── validate() — ProfileValidator ────────────────────────

    @Nested
    @DisplayName("validate() — ProfileValidator")
    class Validation {

        @Test
        @DisplayName("deve ser válido com profile recém-criado (campos opcionais nulos)")
        void deveSerValidoComDefaults() {
            final var notification = Notification.create();
            newProfile().validate(notification);
            assertFalse(notification.hasError(), "profile sem dados não deve ter erros de validação");
        }

        @Test
        @DisplayName("deve ser válido com dados pessoais preenchidos corretamente")
        void deveSerValidoComDadosCorretos() {
            final var profile = newProfile();
            profile.updatePersonalData(
                    "Bruno", "Dias", "529.982.247-25",
                    LocalDate.of(1990, 1, 1),
                    "M", "pt-BR", "BRL", false
            );
            final var notification = Notification.create();
            profile.validate(notification);
            assertFalse(notification.hasError());
        }

        @Test
        @DisplayName("deve reportar erro quando firstName exceder 100 caracteres")
        void deveRejeitarPrimeiroNomeLongo() {
            final var profile = newProfile();
            profile.update("A".repeat(101), null, null);
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
            assertTrue(notification.getErrors().stream()
                    .anyMatch(e -> e.message().contains("100")));
        }

        @Test
        @DisplayName("deve reportar erro quando lastName exceder 100 caracteres")
        void deveRejeitarSobrenomeLongo() {
            final var profile = newProfile();
            profile.update(null, "B".repeat(101), null);
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
        }

        @Test
        @DisplayName("deve aceitar CPF válido sem reportar erros")
        void deveAceitarCpfValido() {
            final var profile = newProfile();
            profile.updatePersonalData(null, null, "529.982.247-25", null, null, null, null, false);
            final var notification = Notification.create();
            profile.validate(notification);
            assertFalse(notification.hasError());
        }

        @Test
        @DisplayName("deve reportar erro para CPF com formato inválido")
        void deveRejeitarCpfInvalido() {
            final var profile = newProfile();
            profile.updatePersonalData(null, null, "000.000.000-00", null, null, null, null, false);
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
            assertTrue(notification.getErrors().stream()
                    .anyMatch(e -> e.message().toLowerCase().contains("cpf")));
        }

        @Test
        @DisplayName("não deve reportar erro quando CPF for nulo (campo opcional)")
        void naoDeveValidarCpfNulo() {
            // CPF nulo = campo não preenchido ainda, não é erro
            final var notification = Notification.create();
            newProfile().validate(notification);
            assertFalse(notification.hasError());
        }

        @Test
        @DisplayName("deve reportar erro quando preferredLanguage for menor que 2 caracteres")
        void deveRejeitarLanguageCurta() {
            final var profile = Profile.with(
                    ProfileId.unique(), anyUserId(),
                    null, null, null, null, null, null, null,
                    "X", "BRL", false, null, null,
                    Instant.now(), Instant.now(), null
            );
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
            assertTrue(notification.getErrors().stream()
                    .anyMatch(e -> e.message().contains("preferredLanguage")));
        }

        @Test
        @DisplayName("deve reportar erro quando preferredLanguage exceder 10 caracteres")
        void deveRejeitarLanguageLonga() {
            final var profile = Profile.with(
                    ProfileId.unique(), anyUserId(),
                    null, null, null, null, null, null, null,
                    "pt-BR-EXTRA1", "BRL", false, null, null,
                    Instant.now(), Instant.now(), null
            );
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
        }

        @Test
        @DisplayName("deve reportar erro quando preferredCurrency não tiver exatamente 3 caracteres")
        void deveRejeitarCurrencyInvalida() {
            final var profile = Profile.with(
                    ProfileId.unique(), anyUserId(),
                    null, null, null, null, null, null, null,
                    "pt-BR", "BR", false, null, null,
                    Instant.now(), Instant.now(), null
            );
            final var notification = Notification.create();
            profile.validate(notification);
            assertTrue(notification.hasError());
            assertTrue(notification.getErrors().stream()
                    .anyMatch(e -> e.message().contains("preferredCurrency")));
        }

        @Test
        @DisplayName("deve aceitar preferredLanguage de 2 e 10 caracteres (limites inclusos)")
        void deveAceitarLanguageNosLimites() {
            // 2 chars — mínimo
            final var profileMin = Profile.with(
                    ProfileId.unique(), anyUserId(),
                    null, null, null, null, null, null, null,
                    "pt", "BRL", false, null, null,
                    Instant.now(), Instant.now(), null
            );
            final var notMin = Notification.create();
            profileMin.validate(notMin);
            assertFalse(notMin.hasError(), "2 chars deve ser aceito");

            // 10 chars — máximo
            final var profileMax = Profile.with(
                    ProfileId.unique(), anyUserId(),
                    null, null, null, null, null, null, null,
                    "1234567890", "BRL", false, null, null,
                    Instant.now(), Instant.now(), null
            );
            final var notMax = Notification.create();
            profileMax.validate(notMax);
            assertFalse(notMax.hasError(), "10 chars deve ser aceito");
        }
    }
}
