package com.btree.application.usecase.catalog.category.create;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.shared.event.DomainEventPublisher;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateCategory use case")
class CreateCategoryUseCaseTest extends UseCaseTest {

    @Mock
    CategoryGateway categoryGateway;

    @Mock
    DomainEventPublisher eventPublisher;

    CreateCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCategoryUseCase(categoryGateway, eventPublisher, new ImmediateTransactionManager());
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve criar categoria raiz com todos os campos e retornar output correto")
    void givenValidRootCommand_whenExecute_thenReturnOutput() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);
        when(categoryGateway.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand());

        assertTrue(result.isRight());
        final var output = result.get();
        assertNotNull(output.id());
        assertNull(output.parentId());
        assertEquals("Esportes", output.name());
        assertEquals("esportes", output.slug());
        assertEquals("Artigos esportivos", output.description());
        assertEquals("https://cdn.example.com/esportes.png", output.imageUrl());
        assertEquals(1, output.sortOrder());
        assertTrue(output.active());
        assertNotNull(output.createdAt());
        assertNotNull(output.updatedAt());

        verify(categoryGateway).existsBySlug("esportes");
        verify(categoryGateway).save(any(Category.class));
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    @DisplayName("Deve criar categoria filha quando parentId valido for informado")
    void givenValidCommandWithParent_whenExecute_thenReturnOutputWithParentId() {
        final var parentId = CategoryId.unique();
        final var parent = buildCategory(parentId, null, "Esportes", "esportes");
        when(categoryGateway.existsBySlug("futebol")).thenReturn(false);
        when(categoryGateway.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryGateway.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new CreateCategoryCommand(
                parentId.getValue().toString(),
                "Futebol", "futebol", null, null, 1
        );
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertEquals(parentId.getValue().toString(), result.get().parentId());
        verify(categoryGateway).findById(parentId);
    }

    @Test
    @DisplayName("Deve criar categoria sem description e imageUrl (campos opcionais)")
    void givenCommandWithoutOptionalFields_whenExecute_thenSucceed() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);
        when(categoryGateway.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new CreateCategoryCommand(null, "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().description());
        assertNull(result.get().imageUrl());
        verify(categoryGateway).save(any(Category.class));
    }

    // ── publicação de eventos ─────────────────────────────────────────────────

    @Test
    @DisplayName("Deve publicar CategoryCreatedEvent apos persistir a categoria")
    void givenValidCommand_whenExecute_thenPublishCategoryCreatedEvent() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);
        when(categoryGateway.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand());

        assertTrue(result.isRight());
        verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
    }

    // ── unicidade de slug ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando slug ja estiver em uso")
    void givenDuplicateSlug_whenExecute_thenReturnSlugAlreadyExists() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(true);

        final var result = useCase.execute(validCommand());

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_ALREADY_EXISTS.message());
        verify(categoryGateway, never()).save(any());
        verify(eventPublisher, never()).publishAll(any());
    }

    @Test
    @DisplayName("Deve ignorar checagem de slug quando slug for nulo")
    void givenNullSlug_whenExecute_thenSkipSlugUniquenessCheck() {
        final var command = new CreateCategoryCommand(null, "Esportes", null, null, null, 0);

        final var result = useCase.execute(command);

        // slug nulo passa a checagem de unicidade mas falha na validação do aggregate
        assertTrue(result.isLeft());
        verify(categoryGateway, never()).existsBySlug(any());
        verify(categoryGateway, never()).save(any());
    }

    // ── validação do parent ───────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando parentId nao for um UUID valido")
    void givenInvalidParentId_whenExecute_thenReturnParentNotFound() {
        when(categoryGateway.existsBySlug("futebol")).thenReturn(false);

        final var command = new CreateCategoryCommand("not-a-uuid", "Futebol", "futebol", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando categoria pai nao existir no banco")
    void givenParentNotFound_whenExecute_thenReturnParentNotFound() {
        final var missingId = UUID.randomUUID().toString();
        when(categoryGateway.existsBySlug("futebol")).thenReturn(false);
        when(categoryGateway.findById(any(CategoryId.class))).thenReturn(Optional.empty());

        final var command = new CreateCategoryCommand(missingId, "Futebol", "futebol", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando categoria pai estiver soft-deletada")
    void givenDeletedParent_whenExecute_thenReturnParentNotFound() {
        final var parentId = CategoryId.unique();
        final var deletedParent = Category.with(
                parentId, null, "Esportes", "esportes", null, null, 1, false,
                Instant.now(), Instant.now(), Instant.now()
        );
        when(categoryGateway.existsBySlug("futebol")).thenReturn(false);
        when(categoryGateway.findById(parentId)).thenReturn(Optional.of(deletedParent));

        final var command = new CreateCategoryCommand(
                parentId.getValue().toString(), "Futebol", "futebol", null, null, 0
        );
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve tratar parentId em branco como categoria raiz")
    void givenBlankParentId_whenExecute_thenTreatAsRoot() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);
        when(categoryGateway.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new CreateCategoryCommand("   ", "Esportes", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().parentId());
        verify(categoryGateway, never()).findById(any());
    }

    // ── validações do aggregate ───────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando name for nulo")
    void givenNullName_whenExecute_thenReturnNameEmptyError() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, null, "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.NAME_EMPTY.message());
        verify(categoryGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando name for em branco")
    void givenBlankName_whenExecute_thenReturnNameEmptyError() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "   ", "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.NAME_EMPTY.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando name ultrapassar 200 caracteres")
    void givenNameTooLong_whenExecute_thenReturnNameTooLongError() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "A".repeat(201), "esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.NAME_TOO_LONG.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug for em branco")
    void givenBlankSlug_whenExecute_thenReturnSlugEmptyError() {
        when(categoryGateway.existsBySlug("   ")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "Esportes", "   ", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_EMPTY.message());
        verify(categoryGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug ultrapassar 256 caracteres")
    void givenSlugTooLong_whenExecute_thenReturnSlugTooLongError() {
        final var longSlug = "a".repeat(257);
        when(categoryGateway.existsBySlug(longSlug)).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "Esportes", longSlug, null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_TOO_LONG.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug tiver formato invalido (letras maiusculas)")
    void givenUpperCaseSlug_whenExecute_thenReturnSlugInvalidFormatError() {
        when(categoryGateway.existsBySlug("Esportes")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "Esportes", "Esportes", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_INVALID_FORMAT.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug contiver espacos")
    void givenSlugWithSpaces_whenExecute_thenReturnSlugInvalidFormatError() {
        when(categoryGateway.existsBySlug("minha categoria")).thenReturn(false);

        final var command = new CreateCategoryCommand(null, "Minha Categoria", "minha categoria", null, null, 0);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), CategoryError.SLUG_INVALID_FORMAT.message());
    }

    // ── acumulação de erros ───────────────────────────────────────────────────

    @Test
    @DisplayName("Deve acumular erros de slug duplicado e parent invalido no mesmo notification")
    void givenDuplicateSlugAndInvalidParent_whenExecute_thenAccumulateBothErrors() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(true);

        final var command = new CreateCategoryCommand(
                "not-a-uuid", "Esportes", "esportes", null, null, 0
        );
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().size() >= 2);
        assertError(result.getLeft(), CategoryError.SLUG_ALREADY_EXISTS.message());
        assertError(result.getLeft(), CategoryError.PARENT_CATEGORY_NOT_FOUND.message());
        verify(categoryGateway, never()).save(any());
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Left quando o gateway lancar excecao ao salvar")
    void givenGatewayException_whenExecute_thenReturnNotification() {
        when(categoryGateway.existsBySlug("esportes")).thenReturn(false);
        when(categoryGateway.save(any(Category.class))).thenThrow(new RuntimeException("db error"));

        final var result = useCase.execute(validCommand());

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(eventPublisher, never()).publishAll(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateCategoryCommand validCommand() {
        return new CreateCategoryCommand(
                null, "Esportes", "esportes", "Artigos esportivos",
                "https://cdn.example.com/esportes.png", 1
        );
    }

    private Category buildCategory(
            final CategoryId id,
            final CategoryId parentId,
            final String name,
            final String slug
    ) {
        return Category.with(id, parentId, name, slug, null, null, 0, true,
                Instant.now(), Instant.now(), null);
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro com mensagem: " + message
        );
    }
}
