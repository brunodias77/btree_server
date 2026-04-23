package com.btree.application.usecase.catalog.category.get_by_id;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCategory use case")
class GetCategoryUseCaseTest extends UseCaseTest {

    private static final String CATEGORY_ID        = UUID.randomUUID().toString();
    private static final String PARENT_CATEGORY_ID = UUID.randomUUID().toString();

    @Mock CategoryGateway categoryGateway;

    GetCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCategoryUseCase(categoryGateway);
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar output completo quando categoria existir e não estiver deletada")
        void givenExistingCategoryId_whenExecute_thenReturnRight() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.of(activeCategory()));

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(CATEGORY_ID,       output.id());
            assertEquals("Moda Feminina",   output.name());
            assertEquals("moda-feminina",   output.slug());
            assertEquals("Roupas femininas",output.description());
            assertEquals(1,                 output.sortOrder());
            assertTrue(output.active());
            assertNotNull(output.createdAt());
            assertNotNull(output.updatedAt());
        }

        @Test
        @DisplayName("Deve mapear parentId corretamente quando categoria tiver categoria pai")
        void givenCategoryWithParent_whenExecute_thenMapParentId() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.of(childCategory()));

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isRight());
            assertEquals(PARENT_CATEGORY_ID, result.get().parentId());
        }

        @Test
        @DisplayName("Deve mapear parentId como null quando categoria for raiz")
        void givenRootCategory_whenExecute_thenParentIdIsNull() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.of(activeCategory()));

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isRight());
            assertNull(result.get().parentId());
        }

        @Test
        @DisplayName("Deve chamar categoryGateway.findById exatamente uma vez")
        void givenExistingCategoryId_whenExecute_thenGatewayCalledOnce() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.of(activeCategory()));

            useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            verify(categoryGateway, times(1)).findById(CategoryId.from(CATEGORY_ID));
        }
    }

    // ── categoria não encontrada ──────────────────────────────────────────────

    @Nested
    @DisplayName("Categoria não encontrada")
    class CategoryNotFound {

        @Test
        @DisplayName("Deve retornar Left com CATEGORY_NOT_FOUND quando categoria não existir")
        void givenNonExistentCategoryId_whenExecute_thenReturnLeft() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.empty());

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), CategoryError.CATEGORY_NOT_FOUND.message());
            verify(categoryGateway, times(1)).findById(CategoryId.from(CATEGORY_ID));
        }

        @Test
        @DisplayName("Deve retornar Left com CATEGORY_NOT_FOUND quando categoria estiver soft-deletada")
        void givenSoftDeletedCategoryId_whenExecute_thenReturnLeft() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.of(deletedCategory()));

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), CategoryError.CATEGORY_NOT_FOUND.message());
        }

        @Test
        @DisplayName("Deve retornar exatamente um erro quando categoria não for encontrada")
        void givenNonExistentCategoryId_whenExecute_thenReturnSingleError() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID)))
                    .thenReturn(Optional.empty());

            final var result = useCase.execute(new GetCategoryCommand(CATEGORY_ID));

            assertTrue(result.isLeft());
            assertEquals(1, result.getLeft().getErrors().size());
        }
    }

    // ── validação do ID ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validação do ID")
    class IdValidation {

        @Test
        @DisplayName("Deve retornar Left com CATEGORY_NOT_FOUND quando categoryId for UUID inválido")
        void givenInvalidUUID_whenExecute_thenReturnLeft() {
            final var result = useCase.execute(new GetCategoryCommand("nao-e-um-uuid"));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), CategoryError.CATEGORY_NOT_FOUND.message());
            verifyNoInteractions(categoryGateway);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Categoria raiz ativa. */
    private Category activeCategory() {
        final var now = Instant.now();
        return Category.with(
                CategoryId.from(CATEGORY_ID),
                null,
                "Moda Feminina", "moda-feminina",
                "Roupas femininas", null,
                1, true,
                now, now, null
        );
    }

    /** Categoria filha (com parentId) ativa. */
    private Category childCategory() {
        final var now = Instant.now();
        return Category.with(
                CategoryId.from(CATEGORY_ID),
                CategoryId.from(PARENT_CATEGORY_ID),
                "Camisetas", "camisetas",
                null, null,
                0, true,
                now, now, null
        );
    }

    /** Categoria soft-deletada. */
    private Category deletedCategory() {
        final var now = Instant.now();
        return Category.with(
                CategoryId.from(CATEGORY_ID),
                null,
                "Moda Feminina", "moda-feminina",
                null, null,
                0, false,
                now, now, now   // deletedAt != null
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
