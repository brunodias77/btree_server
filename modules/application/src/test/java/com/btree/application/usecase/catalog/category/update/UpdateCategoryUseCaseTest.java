package com.btree.application.usecase.catalog.category.update;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateCategory use case")
class UpdateCategoryUseCaseTest extends UseCaseTest {

    @Mock
    CategoryGateway categoryGateway;

    UpdateCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCategoryUseCase(categoryGateway, new ImmediateTransactionManager());
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve atualizar todos os campos e retornar output correto")
    void givenValidCommand_whenExecute_thenReturnUpdatedOutput() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.existsBySlugExcluding("corrida", category.getId())).thenReturn(false);
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(
                id, null, "Corrida", "corrida", "Artigos de corrida", "https://cdn.example.com/corrida.png", 2
        );
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(id, output.id());
        assertNull(output.parentId());
        assertEquals("Corrida", output.name());
        assertEquals("corrida", output.slug());
        assertEquals("Artigos de corrida", output.description());
        assertEquals("https://cdn.example.com/corrida.png", output.imageUrl());
        assertEquals(2, output.sortOrder());
        assertTrue(output.active());
        assertNotNull(output.updatedAt());

        verify(categoryGateway).findById(category.getId());
        verify(categoryGateway).existsBySlugExcluding("corrida", category.getId());
        verify(categoryGateway).update(any(Category.class));
    }

    @Test
    @DisplayName("Deve atualizar mantendo o mesmo slug sem checar unicidade")
    void givenSameSlug_whenExecute_thenSkipSlugUniquenessCheck() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(id, null, "Esportes Updated", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertEquals("Esportes Updated", result.get().name());
        verify(categoryGateway, never()).existsBySlugExcluding(any(), any());
    }

    @Test
    @DisplayName("Deve atualizar com campos opcionais nulos")
    void givenNullOptionalFields_whenExecute_thenSucceed() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(id, null, "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().description());
        assertNull(result.get().imageUrl());
    }

    @Test
    @DisplayName("Deve ignorar checagem de slug quando slug for nulo")
    void givenNullSlug_whenExecute_thenSkipSlugUniquenessCheck() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(id, null, "Esportes", null, null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        verify(categoryGateway, never()).existsBySlugExcluding(any(), any());
    }

    // ── atribuição / remoção de parent ────────────────────────────────────────

    @Test
    @DisplayName("Deve atribuir categoria pai valida e refletir parentId no output")
    void givenValidParentId_whenExecute_thenReturnOutputWithParentId() {
        final var parentId = CategoryId.unique();
        final var parent   = existingCategoryWith(parentId, null, "Esportes", "esportes");
        final var category = existingCategory(null, "Futebol", "futebol");
        final var id       = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(
                id, parentId.getValue().toString(), "Futebol", "futebol", null, null, 0
        );
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertEquals(parentId.getValue().toString(), result.get().parentId());
        verify(categoryGateway).findById(parentId);
    }

    @Test
    @DisplayName("Deve remover parent quando parentId for nulo (torna categoria raiz)")
    void givenNullParentId_whenExecute_thenSetParentToNull() {
        final var parentId = CategoryId.unique();
        final var category = existingCategory(parentId, "Futebol", "futebol");
        final var id       = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(id, null, "Futebol", "futebol", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().parentId());
        verify(categoryGateway, never()).findById(parentId);
    }

    @Test
    @DisplayName("Deve tratar parentId em branco como remocao do parent (categoria raiz)")
    void givenBlankParentId_whenExecute_thenSetParentToNull() {
        final var category = existingCategory(null, "Futebol", "futebol");
        final var id       = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateCategoryCommand(id, "   ", "Futebol", "futebol", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().parentId());
    }

    // ── validação do categoryId ───────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando categoryId for UUID invalido")
    void givenInvalidUUID_whenExecute_thenReturnCategoryNotFound() {
        final var result = useCase.execute(validCommand("not-a-uuid"));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.CATEGORY_NOT_FOUND.message());
        verifyNoInteractions(categoryGateway);
    }

    @Test
    @DisplayName("Deve lancar NullPointerException quando categoryId for nulo (nao ha guarda de null no use case)")
    void givenNullCategoryId_whenExecute_thenThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> useCase.execute(validCommand(null)));
        verifyNoInteractions(categoryGateway);
    }

    // ── categoria não encontrada / deletada ───────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando categoria nao existir no gateway")
    void givenUnknownId_whenExecute_thenReturnCategoryNotFound() {
        when(categoryGateway.findById(any(CategoryId.class))).thenReturn(Optional.empty());

        final var result = useCase.execute(validCommand(UUID.randomUUID().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando categoria estiver soft-deletada")
    void givenDeletedCategory_whenExecute_thenReturnCategoryAlreadyDeleted() {
        final var category = deletedCategory("Esportes", "esportes");

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));

        final var result = useCase.execute(validCommand(category.getId().getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.CATEGORY_ALREADY_DELETED.message());
        verify(categoryGateway, never()).update(any());
    }

    // ── unicidade de slug ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando novo slug ja estiver em uso por outra categoria")
    void givenDuplicateSlug_whenExecute_thenReturnSlugAlreadyExists() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.existsBySlugExcluding("corrida", category.getId())).thenReturn(true);

        final var command = new UpdateCategoryCommand(id, null, "Corrida", "corrida", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_ALREADY_EXISTS.message());
        verify(categoryGateway, never()).update(any());
    }

    // ── validação do parent ───────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando parentId for igual ao proprio categoryId (referencia circular)")
    void givenParentIdEqualsToCategoryId_whenExecute_thenReturnCircularReference() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));

        final var command = new UpdateCategoryCommand(id, id, "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.CIRCULAR_REFERENCE.message());
        verify(categoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando parentId nao for um UUID valido")
    void givenInvalidParentUUID_whenExecute_thenReturnParentNotFound() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));

        final var command = new UpdateCategoryCommand(id, "not-a-uuid", "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando categoria pai nao existir no gateway")
    void givenParentNotFound_whenExecute_thenReturnParentNotFound() {
        final var category  = existingCategory(null, "Futebol", "futebol");
        final var id        = category.getId().getValue().toString();
        final var missingId = UUID.randomUUID().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.findById(argThat(cid -> cid.getValue().toString().equals(missingId))))
                .thenReturn(Optional.empty());

        final var command = new UpdateCategoryCommand(id, missingId, "Futebol", "futebol", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando categoria pai estiver soft-deletada")
    void givenDeletedParent_whenExecute_thenReturnParentNotFound() {
        final var parentId      = CategoryId.unique();
        final var deletedParent = deletedCategoryWith(parentId, "Esportes", "esportes");
        final var category      = existingCategory(null, "Futebol", "futebol");
        final var id            = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.findById(parentId)).thenReturn(Optional.of(deletedParent));

        final var command = new UpdateCategoryCommand(
                id, parentId.getValue().toString(), "Futebol", "futebol", null, null, 0
        );
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).update(any());
    }

    // ── acumulação de erros ───────────────────────────────────────────────────

    @Test
    @DisplayName("Deve acumular erros de slug duplicado e parent invalido no mesmo notification")
    void givenDuplicateSlugAndInvalidParent_whenExecute_thenAccumulateBothErrors() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.existsBySlugExcluding("corrida", category.getId())).thenReturn(true);

        final var command = new UpdateCategoryCommand(
                id, "not-a-uuid", "Corrida", "corrida", null, null, 0
        );
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().size() >= 2);
        assertError(result.getLeft(), CategoryError.SLUG_ALREADY_EXISTS.message());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("Deve acumular erros de referencia circular e slug duplicado")
    void givenCircularReferenceAndDuplicateSlug_whenExecute_thenAccumulateBothErrors() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.existsBySlugExcluding("corrida", category.getId())).thenReturn(true);

        final var command = new UpdateCategoryCommand(id, id, "Corrida", "corrida", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().size() >= 2);
        assertError(result.getLeft(), CategoryError.SLUG_ALREADY_EXISTS.message());
        assertError(result.getLeft(), CategoryError.CIRCULAR_REFERENCE.message());
        verify(categoryGateway, never()).update(any());
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Left quando o gateway lancar excecao ao persistir")
    void givenGatewayException_whenExecute_thenReturnNotification() {
        final var category = existingCategory(null, "Esportes", "esportes");
        final var id = category.getId().getValue().toString();

        when(categoryGateway.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryGateway.update(any(Category.class))).thenThrow(new RuntimeException("db error"));

        final var command = new UpdateCategoryCommand(id, null, "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UpdateCategoryCommand validCommand(final String categoryId) {
        return new UpdateCategoryCommand(categoryId, null, "Esportes", "esportes", null, null, 0);
    }

    private Category existingCategory(final CategoryId parentId, final String name, final String slug) {
        final var now = Instant.now();
        return Category.with(CategoryId.unique(), parentId, name, slug, null, null, 0, true, now, now, null);
    }

    private Category existingCategoryWith(final CategoryId id, final CategoryId parentId,
                                          final String name, final String slug) {
        final var now = Instant.now();
        return Category.with(id, parentId, name, slug, null, null, 0, true, now, now, null);
    }

    private Category deletedCategory(final String name, final String slug) {
        final var now = Instant.now();
        return Category.with(CategoryId.unique(), null, name, slug, null, null, 0, false, now, now, now);
    }

    private Category deletedCategoryWith(final CategoryId id, final String name, final String slug) {
        final var now = Instant.now();
        return Category.with(id, null, name, slug, null, null, 0, false, now, now, now);
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro com mensagem: " + message
        );
    }
}
