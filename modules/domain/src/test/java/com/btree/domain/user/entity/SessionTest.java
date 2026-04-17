package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Session (AggregateRoot)")
public class SessionTest extends UnitTest {

    private static final UserId    VALID_USER_ID     = UserId.unique();
    private static final String    VALID_TOKEN_HASH  = "hashed_refresh_token_value";
    private static final DeviceInfo VALID_DEVICE     = DeviceInfo.of("192.168.1.1", "Chrome/120");
    private static final Instant   FUTURE_EXPIRY     = Instant.now().plusSeconds(3600);

    private static Session validSession() {
        return Session.create(VALID_USER_ID, VALID_TOKEN_HASH, VALID_DEVICE, FUTURE_EXPIRY, Notification.create());
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
            final var a = validSession();
            final var b = Session.create(UserId.unique(), VALID_TOKEN_HASH, VALID_DEVICE, FUTURE_EXPIRY, Notification.create());
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir todos os campos fornecidos")
        void devePersistirCampos() {
            final var session = validSession();

            assertEquals(VALID_USER_ID,    session.getUserId());
            assertEquals(VALID_TOKEN_HASH, session.getRefreshTokenHash());
            assertEquals(VALID_DEVICE,     session.getDeviceInfo());
            assertEquals(FUTURE_EXPIRY,    session.getExpiresAt());
        }

        @Test
        @DisplayName("deve inicializar revoked como false")
        void deveInicializarRevokedFalse() {
            assertFalse(validSession().isRevoked());
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt no momento da criação")
        void devePreencherTimestamps() {
            final var before  = Instant.now();
            final var session = validSession();
            final var after   = Instant.now();

            assertNotNull(session.getCreatedAt());
            assertNotNull(session.getUpdatedAt());
            assertFalse(session.getCreatedAt().isBefore(before));
            assertFalse(session.getCreatedAt().isAfter(after));
        }
    }

    // ─────────────────────────────────────────────────────────
    // create() — validação (SessionValidator)
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — validação")
    class CreateValidation {

        @Test
        @DisplayName("deve lançar DomainException quando userId for nulo")
        void deveLancarExcecaoComUserIdNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> Session.create(null, VALID_TOKEN_HASH, VALID_DEVICE, FUTURE_EXPIRY, Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("userId")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando refreshTokenHash for nulo")
        void deveLancarExcecaoComRefreshTokenNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> Session.create(VALID_USER_ID, null, VALID_DEVICE, FUTURE_EXPIRY, Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("refreshTokenHash")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando refreshTokenHash for vazio")
        void deveLancarExcecaoComRefreshTokenVazio() {
            final var ex = assertThrows(DomainException.class,
                    () -> Session.create(VALID_USER_ID, "   ", VALID_DEVICE, FUTURE_EXPIRY, Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("refreshTokenHash")));
        }

        @Test
        @DisplayName("deve lançar DomainException quando expiresAt for nulo")
        void deveLancarExcecaoComExpiresAtNulo() {
            final var ex = assertThrows(DomainException.class,
                    () -> Session.create(VALID_USER_ID, VALID_TOKEN_HASH, VALID_DEVICE, null, Notification.create()));
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.message().contains("expiresAt")));
        }

        @Test
        @DisplayName("deve acumular múltiplos erros quando vários campos estiverem inválidos")
        void deveAcumularMultiplosErros() {
            final var ex = assertThrows(DomainException.class,
                    () -> Session.create(null, null, VALID_DEVICE, null, Notification.create()));
            assertTrue(ex.getErrors().size() >= 3,
                    "Deve ter ao menos 3 erros (userId, refreshTokenHash, expiresAt), mas teve: " + ex.getErrors().size());
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos de uma sessão ativa")
        void deveReconstituirSessaoAtiva() {
            final var id        = SessionId.unique();
            final var userId    = UserId.unique();
            final var now       = Instant.now();
            final var expiresAt = now.plusSeconds(7200);

            final var session = Session.with(
                    id, userId, "some_hash", VALID_DEVICE, expiresAt, false, now, now, 1
            );

            assertEquals(id,          session.getId());
            assertEquals(userId,      session.getUserId());
            assertEquals("some_hash", session.getRefreshTokenHash());
            assertEquals(VALID_DEVICE, session.getDeviceInfo());
            assertEquals(expiresAt,   session.getExpiresAt());
            assertFalse(session.isRevoked());
            assertEquals(now,         session.getCreatedAt());
        }

        @Test
        @DisplayName("deve reconstituir sessão revogada")
        void deveReconstituirSessaoRevogada() {
            final var now = Instant.now();
            final var session = Session.with(
                    SessionId.unique(), UserId.unique(), "hash", null,
                    now.plusSeconds(3600), true, now, now, 2
            );
            assertTrue(session.isRevoked());
        }
    }

    // ─────────────────────────────────────────────────────────
    // revoke()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        @DisplayName("deve marcar revoked como true após revoke()")
        void deveMarcarRevoked() {
            final var session = validSession();
            assertFalse(session.isRevoked(), "pré-condição: não revogada");
            session.revoke();
            assertTrue(session.isRevoked());
        }

        @Test
        @DisplayName("deve atualizar updatedAt ao revogar")
        void deveAtualizarUpdatedAt() throws InterruptedException {
            final var session = validSession();
            final var before  = session.getUpdatedAt();
            Thread.sleep(5);
            session.revoke();
            assertTrue(session.getUpdatedAt().isAfter(before));
        }
    }

    // ─────────────────────────────────────────────────────────
    // isExpired() / isActive()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isExpired() e isActive()")
    class ExpirationAndActivity {

        @Test
        @DisplayName("isExpired() deve retornar false para sessão com expiresAt no futuro")
        void naoDeveEstarExpiradaQuandoNoFuturo() {
            assertFalse(validSession().isExpired());
        }

        @Test
        @DisplayName("isExpired() deve retornar true para sessão com expiresAt no passado")
        void deveEstarExpiradaQuandoNoPassado() {
            final var now = Instant.now();
            final var session = Session.with(
                    SessionId.unique(), UserId.unique(), VALID_TOKEN_HASH, null,
                    now.minusSeconds(1), false, now, now, 0
            );
            assertTrue(session.isExpired());
        }

        @Test
        @DisplayName("isActive() deve retornar true para sessão não revogada e não expirada")
        void deveEstarAtiva() {
            assertTrue(validSession().isActive());
        }

        @Test
        @DisplayName("isActive() deve retornar false para sessão revogada")
        void naoDeveEstarAtivaSeRevogada() {
            final var session = validSession();
            session.revoke();
            assertFalse(session.isActive());
        }

        @Test
        @DisplayName("isActive() deve retornar false para sessão expirada")
        void naoDeveEstarAtivaSeExpirada() {
            final var now = Instant.now();
            final var session = Session.with(
                    SessionId.unique(), UserId.unique(), VALID_TOKEN_HASH, null,
                    now.minusSeconds(1), false, now, now, 0
            );
            assertFalse(session.isActive());
        }
    }
}
