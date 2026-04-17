package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.RoleId;
import com.btree.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Role (Entity)")
public class RoleTest extends UnitTest {

    // ─────────────────────────────────────────────────────────
    // create()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — campos e defaults")
    class CreateFactory {

        @Test
        @DisplayName("deve gerar ID único e não nulo")
        void deveGerarIdUnico() {
            final var a = Role.create("ADMIN", null);
            final var b = Role.create("MANAGER", null);
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir name e description fornecidos")
        void devePersistirCampos() {
            final var role = Role.create("ADMIN", "Administrador do sistema");
            assertEquals("ADMIN", role.getName());
            assertEquals("Administrador do sistema", role.getDescription());
        }

        @Test
        @DisplayName("deve aceitar description nula — campo opcional")
        void deveAceitarDescriptionNula() {
            final var role = Role.create("USER", null);
            assertNull(role.getDescription());
        }

        @Test
        @DisplayName("deve preencher createdAt próximo ao instante de criação")
        void devePreencherCreatedAt() {
            final var before = Instant.now();
            final var role   = Role.create("USER", null);
            final var after  = Instant.now();

            assertNotNull(role.getCreatedAt());
            assertFalse(role.getCreatedAt().isBefore(before));
            assertFalse(role.getCreatedAt().isAfter(after));
        }
    }

    // ─────────────────────────────────────────────────────────
    // create() — validação
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — validação do name")
    class CreateValidation {

        @ParameterizedTest(name = "name=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("deve lançar DomainException quando name for nulo, vazio ou somente espaços")
        void deveLancarExcecaoComNameInvalido(String name) {
            assertThrows(DomainException.class,
                    () -> Role.create(name, "qualquer descrição"),
                    "Role.create() com name=[" + name + "] deveria lançar DomainException");
        }

        @Test
        @DisplayName("deve aceitar name com letras, números e underscores")
        void deveAceitarNameValido() {
            assertDoesNotThrow(() -> Role.create("SUPER_ADMIN", null));
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
            final var id        = RoleId.unique();
            final var createdAt = Instant.now().minusSeconds(3600);

            final var role = Role.with(id, "AUDITOR", "Auditoria", createdAt);

            assertEquals(id,        role.getId());
            assertEquals("AUDITOR", role.getName());
            assertEquals("Auditoria", role.getDescription());
            assertEquals(createdAt, role.getCreatedAt());
        }

        @Test
        @DisplayName("with() não deve executar validação — deve aceitar name nulo")
        void naoDeveValidar() {
            assertDoesNotThrow(() -> Role.with(RoleId.unique(), null, null, Instant.now()),
                    "with() não deve validar — confia nos dados do banco");
        }
    }
}
