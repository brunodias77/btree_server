package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserNotificationId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserNotification (Entity)")
public class UserNotificationTest extends UnitTest {

    private static UserId anyUserId() {
        return UserId.unique();
    }

    private static UserNotification validNotification() {
        return UserNotification.create(anyUserId(), "Pedido confirmado", "Seu pedido foi confirmado.", "ORDER_UPDATE");
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
            final var a = validNotification();
            final var b = validNotification();
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir userId, title, message e notificationType")
        void devePersistirCampos() {
            final var userId = anyUserId();
            final var notif  = UserNotification.create(userId, "Título", "Mensagem", "PROMO");

            assertEquals(userId,   notif.getUserId());
            assertEquals("Título", notif.getTitle());
            assertEquals("Mensagem", notif.getMessage());
            assertEquals("PROMO",  notif.getNotificationType());
        }

        @Test
        @DisplayName("deve inicializar campos opcionais como nulos")
        void deveInicializarCamposOpcionaisNulos() {
            final var notif = validNotification();
            assertNull(notif.getReferenceType());
            assertNull(notif.getReferenceId());
            assertNull(notif.getActionUrl());
            assertNull(notif.getReadAt());
        }

        @Test
        @DisplayName("deve preencher createdAt próximo ao instante de criação")
        void devePreencherCreatedAt() {
            final var before = Instant.now();
            final var notif  = validNotification();
            final var after  = Instant.now();

            assertNotNull(notif.getCreatedAt());
            assertFalse(notif.getCreatedAt().isBefore(before));
            assertFalse(notif.getCreatedAt().isAfter(after));
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos incluindo os opcionais")
        void deveReconstituirTodosCampos() {
            final var id          = UserNotificationId.unique();
            final var userId      = anyUserId();
            final var referenceId = UUID.randomUUID();
            final var readAt      = Instant.now().minusSeconds(60);
            final var createdAt   = Instant.now().minusSeconds(3600);

            final var notif = UserNotification.with(
                    id, userId,
                    "Título", "Mensagem", "ORDER_UPDATE",
                    "ORDER", referenceId,
                    "https://app.example.com/orders/123",
                    readAt, createdAt
            );

            assertEquals(id,            notif.getId());
            assertEquals(userId,        notif.getUserId());
            assertEquals("Título",      notif.getTitle());
            assertEquals("Mensagem",    notif.getMessage());
            assertEquals("ORDER_UPDATE", notif.getNotificationType());
            assertEquals("ORDER",       notif.getReferenceType());
            assertEquals(referenceId,   notif.getReferenceId());
            assertEquals("https://app.example.com/orders/123", notif.getActionUrl());
            assertEquals(readAt,        notif.getReadAt());
            assertEquals(createdAt,     notif.getCreatedAt());
        }

        @Test
        @DisplayName("deve reconstituir notificação não lida quando readAt for nulo")
        void deveReconstituirNaoLida() {
            final var notif = UserNotification.with(
                    UserNotificationId.unique(), anyUserId(),
                    "T", "M", "TYPE", null, null, null, null, Instant.now()
            );
            assertFalse(notif.isRead());
        }
    }

    // ─────────────────────────────────────────────────────────
    // markAsRead() / isRead()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markAsRead() e isRead()")
    class ReadState {

        @Test
        @DisplayName("isRead() deve retornar false para notificação recém-criada")
        void deveEstarNaoLidaNoInicio() {
            assertFalse(validNotification().isRead());
        }

        @Test
        @DisplayName("markAsRead() deve preencher readAt")
        void devePreencherReadAt() {
            final var notif  = validNotification();
            final var before = Instant.now();
            notif.markAsRead();
            final var after  = Instant.now();

            assertNotNull(notif.getReadAt());
            assertFalse(notif.getReadAt().isBefore(before));
            assertFalse(notif.getReadAt().isAfter(after));
        }

        @Test
        @DisplayName("isRead() deve retornar true após markAsRead()")
        void deveEstarLidaAposMarcar() {
            final var notif = validNotification();
            notif.markAsRead();
            assertTrue(notif.isRead());
        }

        @Test
        @DisplayName("markAsRead() não deve sobrescrever readAt quando já estiver preenchido")
        void naoDeveReescreverReadAt() throws InterruptedException {
            final var notif = validNotification();
            notif.markAsRead();
            final var firstReadAt = notif.getReadAt();
            Thread.sleep(5);
            notif.markAsRead();
            assertEquals(firstReadAt, notif.getReadAt(), "readAt não deve ser alterado na segunda chamada");
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — regras de obrigatoriedade")
    class Validation {

        @Test
        @DisplayName("deve reportar erro quando title for nulo")
        void deveRejeitarTitleNulo() {
            final var notif         = UserNotification.create(anyUserId(), null, "Msg", "TYPE");
            final var notification  = Notification.create();
            notif.validate(notification);
            assertTrue(notification.hasError());
            assertTrue(notification.getErrors().stream().anyMatch(e -> e.message().toLowerCase().contains("title")));
        }

        @Test
        @DisplayName("deve reportar erro quando title for vazio")
        void deveRejeitarTitleVazio() {
            final var notif        = UserNotification.create(anyUserId(), "  ", "Msg", "TYPE");
            final var notification = Notification.create();
            notif.validate(notification);
            assertTrue(notification.hasError());
        }

        @Test
        @DisplayName("deve reportar erro quando message for nula ou vazia")
        void deveRejeitarMessageNulaOuVazia() {
            for (var msg : new String[]{null, "  "}) {
                final var notif        = UserNotification.create(anyUserId(), "Título", msg, "TYPE");
                final var notification = Notification.create();
                notif.validate(notification);
                assertTrue(notification.hasError(), "message=[" + msg + "] deve ter erro");
            }
        }

        @Test
        @DisplayName("deve reportar erro quando notificationType for nulo ou vazio")
        void deveRejeitarNotificationTypeNuloOuVazio() {
            for (var type : new String[]{null, "  "}) {
                final var notif        = UserNotification.create(anyUserId(), "Título", "Msg", type);
                final var notification = Notification.create();
                notif.validate(notification);
                assertTrue(notification.hasError(), "notificationType=[" + type + "] deve ter erro");
            }
        }

        @Test
        @DisplayName("não deve ter erros quando todos os campos obrigatórios estiverem preenchidos")
        void devePassarValidacaoComCamposValidos() {
            final var notification = Notification.create();
            validNotification().validate(notification);
            assertFalse(notification.hasError());
        }
    }
}
