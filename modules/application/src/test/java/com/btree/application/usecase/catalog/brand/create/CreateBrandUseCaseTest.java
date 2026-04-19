package com.btree.application.usecase.catalog.brand.create;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.error.BrandError;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.shared.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateBrand use case")
class CreateBrandUseCaseTest extends UseCaseTest {

    @Mock
    BrandGateway brandGateway;

    @Mock
    DomainEventPublisher eventPublisher;

    CreateBrandUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateBrandUseCase(brandGateway, eventPublisher, new ImmediateTransactionManager());
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve criar marca com todos os campos preenchidos e retornar output correto")
    void givenValidCommand_whenExecute_thenReturnCreateBrandOutput() {
        when(brandGateway.existsBySlug("nike")).thenReturn(false);
        when(brandGateway.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand());

        assertTrue(result.isRight());
        final var output = result.get();
        assertNotNull(output.id());
        assertEquals("Nike", output.name());
        assertEquals("nike", output.slug());
        assertEquals("Marca esportiva", output.description());
        assertEquals("https://cdn.example.com/nike.png", output.logoUrl());
        assertNotNull(output.createdAt());
        assertNotNull(output.updatedAt());

        verify(brandGateway).existsBySlug("nike");
        verify(brandGateway).save(any(Brand.class));
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    @DisplayName("Deve criar marca sem description e logoUrl (campos opcionais)")
    void givenCommandWithoutOptionalFields_whenExecute_thenSucceed() {
        when(brandGateway.existsBySlug("adidas")).thenReturn(false);
        when(brandGateway.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new CreateBrandCommand("Adidas", "adidas", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertNull(result.get().description());
        assertNull(result.get().logoUrl());
        verify(brandGateway).save(any(Brand.class));
    }

    @Test
    @DisplayName("Deve publicar BrandCreatedEvent apos persistir a marca")
    void givenValidCommand_whenExecute_thenPublishBrandCreatedEvent() {
        when(brandGateway.existsBySlug("puma")).thenReturn(false);
        when(brandGateway.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(new CreateBrandCommand("Puma", "puma", null, null));

        assertTrue(result.isRight());
        verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
    }

    // ── unicidade de slug ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando slug ja estiver em uso")
    void givenDuplicateSlug_whenExecute_thenReturnSlugAlreadyExists() {
        when(brandGateway.existsBySlug("nike")).thenReturn(true);

        final var result = useCase.execute(validCommand());

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_ALREADY_EXISTS.message());
        verify(brandGateway, never()).save(any());
        verify(eventPublisher, never()).publishAll(any());
    }

    @Test
    @DisplayName("Deve ignorar checagem de slug quando slug for nulo")
    void givenNullSlug_whenExecute_thenSkipSlugUniquenessCheck() {
        final var command = new CreateBrandCommand("Nike", null, null, null);

        final var result = useCase.execute(command);

        // slug nulo passa a checagem de unicidade mas falha na validação do aggregate
        assertTrue(result.isLeft());
        verify(brandGateway, never()).existsBySlug(any());
    }

    // ── validações do aggregate ───────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando name for nulo")
    void givenNullName_whenExecute_thenReturnNameEmptyError() {
        when(brandGateway.existsBySlug("nike")).thenReturn(false);

        final var command = new CreateBrandCommand(null, "nike", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.NAME_EMPTY.message());
        verify(brandGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando name for em branco")
    void givenBlankName_whenExecute_thenReturnNameEmptyError() {
        when(brandGateway.existsBySlug("nike")).thenReturn(false);

        final var command = new CreateBrandCommand("   ", "nike", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.NAME_EMPTY.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando name ultrapassar 200 caracteres")
    void givenNameTooLong_whenExecute_thenReturnNameTooLongError() {
        when(brandGateway.existsBySlug("nike")).thenReturn(false);

        final var longName = "A".repeat(201);
        final var command = new CreateBrandCommand(longName, "nike", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.NAME_TOO_LONG.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug for em branco")
    void givenBlankSlug_whenExecute_thenReturnSlugEmptyError() {
        final var command = new CreateBrandCommand("Nike", "   ", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_EMPTY.message());
        verify(brandGateway, never()).existsBySlug(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug ultrapassar 256 caracteres")
    void givenSlugTooLong_whenExecute_thenReturnSlugTooLongError() {
        final var longSlug = "a".repeat(257);
        when(brandGateway.existsBySlug(longSlug)).thenReturn(false);

        final var command = new CreateBrandCommand("Nike", longSlug, null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_TOO_LONG.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug tiver formato invalido (letras maiusculas)")
    void givenUpperCaseSlug_whenExecute_thenReturnSlugInvalidFormatError() {
        when(brandGateway.existsBySlug("Nike")).thenReturn(false);

        final var command = new CreateBrandCommand("Nike", "Nike", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_INVALID_FORMAT.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug contiver espacos")
    void givenSlugWithSpaces_whenExecute_thenReturnSlugInvalidFormatError() {
        when(brandGateway.existsBySlug("minha marca")).thenReturn(false);

        final var command = new CreateBrandCommand("Minha Marca", "minha marca", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_INVALID_FORMAT.message());
    }

    @Test
    @DisplayName("Deve retornar erro quando slug comecar com hifen")
    void givenSlugStartingWithHyphen_whenExecute_thenReturnSlugInvalidFormatError() {
        when(brandGateway.existsBySlug("-nike")).thenReturn(false);

        final var command = new CreateBrandCommand("Nike", "-nike", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), BrandError.SLUG_INVALID_FORMAT.message());
    }

    @Test
    @DisplayName("Deve acumular erros de name e slug no mesmo notification")
    void givenInvalidNameAndSlug_whenExecute_thenAccumulateBothErrors() {
        when(brandGateway.existsBySlug("Nike Brand")).thenReturn(false);

        final var command = new CreateBrandCommand(null, "Nike Brand", null, null);
        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().size() >= 2);
        assertError(result.getLeft(), BrandError.NAME_EMPTY.message());
        assertError(result.getLeft(), BrandError.SLUG_INVALID_FORMAT.message());
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar erro quando o gateway lancar excecao ao salvar")
    void givenGatewayException_whenExecute_thenReturnNotification() {
        when(brandGateway.existsBySlug("nike")).thenReturn(false);
        when(brandGateway.save(any(Brand.class))).thenThrow(new RuntimeException("db error"));

        final var result = useCase.execute(validCommand());

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(eventPublisher, never()).publishAll(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateBrandCommand validCommand() {
        return new CreateBrandCommand(
                "Nike",
                "nike",
                "Marca esportiva",
                "https://cdn.example.com/nike.png"
        );
    }

    private void assertError(final com.btree.shared.validation.Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro com mensagem: " + message
        );
    }
}
