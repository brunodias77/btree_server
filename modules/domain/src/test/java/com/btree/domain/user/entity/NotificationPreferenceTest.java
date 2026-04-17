package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.NotificationPreferenceId;
import com.btree.domain.user.identifier.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationPreference (Entity)")
public class NotificationPreferenceTest extends UnitTest {

    private static UserId anyUserId() {
        return UserId.unique();
    }

    // ─────────────────────────────────────────────────────────
    // create()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — campos e defaults")
    class CreateFactory {

        @Test
        @DisplayName("deve gerar ID único a cada chamada")
        void deveGerarIdUnico() {
            final var userId = anyUserId();
            final var a = NotificationPreference.create(userId);
            final var b = NotificationPreference.create(userId);
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve associar o userId corretamente")
        void deveAssociarUserId() {
            final var userId = anyUserId();
            final var pref   = NotificationPreference.create(userId);
            assertEquals(userId, pref.getUserId());
        }

        @Test
        @DisplayName("deve ativar email e push por padrão")
        void deveAtivarEmailEPushPorPadrao() {
            final var pref = NotificationPreference.create(anyUserId());
            assertTrue(pref.isEmailEnabled(), "email deve estar ativo por padrão");
            assertTrue(pref.isPushEnabled(),  "push deve estar ativo por padrão");
        }

        @Test
        @DisplayName("deve desativar SMS por padrão")
        void deveDesativarSmsPorPadrao() {
            final var pref = NotificationPreference.create(anyUserId());
            assertFalse(pref.isSmsEnabled(), "SMS deve estar desativado por padrão");
        }

        @Test
        @DisplayName("deve ativar orderUpdates, promotions, priceDrops e backInStock por padrão")
        void deveAtivarTiposDeNotificacaoPorPadrao() {
            final var pref = NotificationPreference.create(anyUserId());
            assertTrue(pref.isOrderUpdates());
            assertTrue(pref.isPromotions());
            assertTrue(pref.isPriceDrops());
            assertTrue(pref.isBackInStock());
        }

        @Test
        @DisplayName("deve desativar newsletter por padrão")
        void deveDesativarNewsletterPorPadrao() {
            final var pref = NotificationPreference.create(anyUserId());
            assertFalse(pref.isNewsletter(), "newsletter deve estar desativada por padrão");
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt no momento da criação")
        void devePreencherTimestamps() {
            final var before = Instant.now();
            final var pref   = NotificationPreference.create(anyUserId());
            final var after  = Instant.now();

            assertNotNull(pref.getCreatedAt());
            assertNotNull(pref.getUpdatedAt());
            assertFalse(pref.getCreatedAt().isBefore(before));
            assertFalse(pref.getCreatedAt().isAfter(after));
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
            final var id     = NotificationPreferenceId.unique();
            final var userId = anyUserId();
            final var now    = Instant.now();

            final var pref = NotificationPreference.with(
                    id, userId,
                    false, false, true,
                    false, true, false, true, true,
                    now, now
            );

            assertEquals(id,     pref.getId());
            assertEquals(userId, pref.getUserId());
            assertFalse(pref.isEmailEnabled());
            assertFalse(pref.isPushEnabled());
            assertTrue(pref.isSmsEnabled());
            assertFalse(pref.isOrderUpdates());
            assertTrue(pref.isPromotions());
            assertFalse(pref.isPriceDrops());
            assertTrue(pref.isBackInStock());
            assertTrue(pref.isNewsletter());
            assertEquals(now, pref.getCreatedAt());
            assertEquals(now, pref.getUpdatedAt());
        }
    }

    // ─────────────────────────────────────────────────────────
    // update()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update() — atualização das preferências")
    class Update {

        @Test
        @DisplayName("deve atualizar todos os canais e tipos de notificação")
        void deveAtualizarTodosCampos() {
            final var pref = NotificationPreference.create(anyUserId());

            pref.update(false, false, true, false, false, false, false, true);

            assertFalse(pref.isEmailEnabled());
            assertFalse(pref.isPushEnabled());
            assertTrue(pref.isSmsEnabled());
            assertFalse(pref.isOrderUpdates());
            assertFalse(pref.isPromotions());
            assertFalse(pref.isPriceDrops());
            assertFalse(pref.isBackInStock());
            assertTrue(pref.isNewsletter());
        }

        @Test
        @DisplayName("deve atualizar updatedAt ao chamar update()")
        void deveAtualizarUpdatedAt() throws InterruptedException {
            final var pref   = NotificationPreference.create(anyUserId());
            final var before = pref.getUpdatedAt();
            Thread.sleep(5);
            pref.update(true, true, false, true, true, true, true, false);
            assertTrue(pref.getUpdatedAt().isAfter(before));
        }

        @Test
        @DisplayName("deve preservar createdAt após atualização")
        void devePreservarCreatedAt() throws InterruptedException {
            final var pref      = NotificationPreference.create(anyUserId());
            final var createdAt = pref.getCreatedAt();
            Thread.sleep(5);
            pref.update(false, false, false, false, false, false, false, false);
            assertEquals(createdAt, pref.getCreatedAt());
        }
    }
}
