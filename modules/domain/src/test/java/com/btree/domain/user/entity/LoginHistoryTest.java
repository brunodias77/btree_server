package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.LoginHistoryId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoginHistory (Entity)")
public class LoginHistoryTest extends UnitTest {

    private static final DeviceInfo DEVICE = DeviceInfo.of("127.0.0.1", "Mozilla/5.0");

    // ─────────────────────────────────────────────────────────
    // recordSuccess()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordSuccess() — campos e defaults")
    class RecordSuccess {

        @Test
        @DisplayName("deve gerar ID único e não nulo")
        void deveGerarIdUnico() {
            final var userId = UserId.unique();
            final var a = LoginHistory.recordSuccess(userId, DEVICE, Notification.create());
            final var b = LoginHistory.recordSuccess(userId, DEVICE, Notification.create());
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir userId e deviceInfo fornecidos")
        void devePersistirCampos() {
            final var userId = UserId.unique();
            final var entry = LoginHistory.recordSuccess(userId, DEVICE, Notification.create());

            assertEquals(userId, entry.getUserId());
            assertEquals(DEVICE, entry.getDeviceInfo());
        }

        @Test
        @DisplayName("deve marcar success como true")
        void deveMarcarSuccessTrue() {
            final var entry = LoginHistory.recordSuccess(UserId.unique(), DEVICE, Notification.create());
            assertTrue(entry.isSuccess());
        }

        @Test
        @DisplayName("deve inicializar failureReason como nulo")
        void deveInicializarFailureReasonNulo() {
            final var entry = LoginHistory.recordSuccess(UserId.unique(), DEVICE, Notification.create());
            assertNull(entry.getFailureReason());
        }

        @Test
        @DisplayName("deve preencher attemptedAt próximo ao instante de criação")
        void devePreencherAttemptedAt() {
            final var before = Instant.now();
            final var entry  = LoginHistory.recordSuccess(UserId.unique(), DEVICE, Notification.create());
            final var after  = Instant.now();

            assertNotNull(entry.getAttemptedAt());
            assertFalse(entry.getAttemptedAt().isBefore(before));
            assertFalse(entry.getAttemptedAt().isAfter(after));
        }

        @Test
        @DisplayName("deve lançar DomainException quando userId for nulo")
        void deveLancarExcecaoComUserIdNulo() {
            assertThrows(DomainException.class,
                    () -> LoginHistory.recordSuccess(null, DEVICE, Notification.create()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // recordFailure()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordFailure() — campos e defaults")
    class RecordFailure {

        @Test
        @DisplayName("deve gerar ID único e não nulo")
        void deveGerarIdUnico() {
            final var a = LoginHistory.recordFailure(UserId.unique(), DEVICE, "INVALID_CREDENTIALS", Notification.create());
            final var b = LoginHistory.recordFailure(UserId.unique(), DEVICE, "INVALID_CREDENTIALS", Notification.create());
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve marcar success como false")
        void deveMarcarSuccessFalse() {
            final var entry = LoginHistory.recordFailure(UserId.unique(), DEVICE, "INVALID_CREDENTIALS", Notification.create());
            assertFalse(entry.isSuccess());
        }

        @Test
        @DisplayName("deve persistir failureReason fornecido")
        void devePersistirFailureReason() {
            final var entry = LoginHistory.recordFailure(UserId.unique(), DEVICE, "INVALID_CREDENTIALS", Notification.create());
            assertEquals("INVALID_CREDENTIALS", entry.getFailureReason());
        }

        @Test
        @DisplayName("deve aceitar userId nulo — usuário desconhecido ainda pode registrar falha")
        void deveAceitarUserIdNulo() {
            assertDoesNotThrow(() ->
                    LoginHistory.recordFailure(null, DEVICE, "UNKNOWN_USER", Notification.create()));
        }

        @Test
        @DisplayName("deve preencher attemptedAt próximo ao instante de criação")
        void devePreencherAttemptedAt() {
            final var before = Instant.now();
            final var entry  = LoginHistory.recordFailure(null, DEVICE, "UNKNOWN", Notification.create());
            final var after  = Instant.now();

            assertNotNull(entry.getAttemptedAt());
            assertFalse(entry.getAttemptedAt().isBefore(before));
            assertFalse(entry.getAttemptedAt().isAfter(after));
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos de um login bem-sucedido")
        void deveReconstituirLoginSucesso() {
            final var id          = LoginHistoryId.unique();
            final var userId      = UserId.unique();
            final var attemptedAt = Instant.now().minusSeconds(60);

            final var entry = LoginHistory.with(id, userId, DEVICE, true, null, attemptedAt);

            assertEquals(id,          entry.getId());
            assertEquals(userId,      entry.getUserId());
            assertEquals(DEVICE,      entry.getDeviceInfo());
            assertTrue(entry.isSuccess());
            assertNull(entry.getFailureReason());
            assertEquals(attemptedAt, entry.getAttemptedAt());
        }

        @Test
        @DisplayName("deve reconstituir todos os campos de um login malsucedido")
        void deveReconstituirLoginFalha() {
            final var id          = LoginHistoryId.unique();
            final var attemptedAt = Instant.now().minusSeconds(30);

            final var entry = LoginHistory.with(id, null, DEVICE, false, "ACCOUNT_LOCKED", attemptedAt);

            assertEquals(id,             entry.getId());
            assertNull(entry.getUserId());
            assertFalse(entry.isSuccess());
            assertEquals("ACCOUNT_LOCKED", entry.getFailureReason());
            assertEquals(attemptedAt,    entry.getAttemptedAt());
        }
    }
}
