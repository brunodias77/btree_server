package com.btree.application.usecase.catalog.product.create;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProduct use case")
class CreateProductUseCaseTest extends UseCaseTest {

    @Mock ProductGateway    productGateway;
    @Mock DomainEventPublisher eventPublisher;

    CreateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProductUseCase(productGateway, eventPublisher, new ImmediateTransactionManager());
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve criar produto com campos obrigatórios e retornar output correto")
        void givenValidCommand_whenExecute_thenReturnCreateProductOutput() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            final var output = result.get();
            assertNotNull(output.id());
            assertEquals("Camiseta Nike", output.name());
            assertEquals("camiseta-nike", output.slug());
            assertEquals("CAMISETA-001", output.sku());
            assertEquals(new BigDecimal("99.90"), output.price());
            assertNotNull(output.createdAt());
            assertNotNull(output.updatedAt());
        }

        @Test
        @DisplayName("Deve criar produto em status DRAFT com featured=false e quantity=0")
        void givenValidCommand_whenExecute_thenProductStartsAsDraftWithDefaults() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var output = useCase.execute(validCommand()).get();

            assertEquals(com.btree.shared.enums.ProductStatus.DRAFT, output.status());
            assertFalse(output.featured());
            assertEquals(0, output.quantity());
        }

        @Test
        @DisplayName("Deve criar produto sem categoryId e brandId (campos opcionais nulos)")
        void givenCommandWithNullCategoryAndBrand_whenExecute_thenSucceed() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            assertNull(result.get().categoryId());
            assertNull(result.get().brandId());
        }

        @Test
        @DisplayName("Deve criar produto com categoryId e brandId preenchidos")
        void givenCommandWithCategoryAndBrand_whenExecute_thenMapIds() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            final var catId  = java.util.UUID.randomUUID().toString();
            final var brandId = java.util.UUID.randomUUID().toString();

            final var cmd = new CreateProductCommand(
                    catId, brandId,
                    "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, null, null, null, null, List.of()
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals(catId,  result.get().categoryId());
            assertEquals(brandId, result.get().brandId());
        }

        @Test
        @DisplayName("Deve publicar evento após persistir produto")
        void givenValidCommand_whenExecute_thenPublishDomainEvent() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
        }

        @Test
        @DisplayName("Deve delegar ao gateway exatamente uma vez")
        void givenValidCommand_whenExecute_thenGatewayCalledOnce() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(validCommand());

            verify(productGateway, times(1)).save(any(Product.class));
            verify(productGateway, times(1)).existsBySlug("camiseta-nike");
            verify(productGateway, times(1)).existsBySku("CAMISETA-001");
        }

        @Test
        @DisplayName("Deve criar produto com imagens e mapeá-las no output")
        void givenCommandWithImages_whenExecute_thenImagesAppearedInOutput() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            final var images = List.of(
                    new CreateProductCommand.ImageEntry("https://cdn.example.com/img1.jpg", "alt 1"),
                    new CreateProductCommand.ImageEntry("https://cdn.example.com/img2.jpg", "alt 2")
            );

            final var cmd = commandWithImages(images);
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals(2, result.get().images().size());
            assertTrue(result.get().images().get(0).primary());
            assertEquals("https://cdn.example.com/img1.jpg", result.get().images().get(0).url());
        }

        @Test
        @DisplayName("Deve criar produto com lista de imagens nula sem falhar")
        void givenNullImages_whenExecute_thenSucceedWithNoImages() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new CreateProductCommand(
                    null, null, "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, null, null, null, null, null
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertTrue(result.get().images().isEmpty());
        }

        @Test
        @DisplayName("Deve criar produto com dimensões físicas preenchidas")
        void givenCommandWithDimensions_whenExecute_thenDimensionsMappedInOutput() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new CreateProductCommand(
                    null, null, "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5,
                    new BigDecimal("0.3"),   // weight
                    new BigDecimal("50.0"),  // width
                    new BigDecimal("70.0"),  // height
                    new BigDecimal("1.0"),   // depth
                    List.of()
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals(new BigDecimal("0.3"),  result.get().weight());
            assertEquals(new BigDecimal("50.0"), result.get().width());
            assertEquals(new BigDecimal("70.0"), result.get().height());
            assertEquals(new BigDecimal("1.0"),  result.get().depth());
        }
    }

    // ── unicidade de slug e sku ───────────────────────────────────────────────

    @Nested
    @DisplayName("Unicidade de slug e SKU")
    class UniquenessChecks {

        @Test
        @DisplayName("Deve retornar erro quando slug já estiver em uso")
        void givenDuplicateSlug_whenExecute_thenReturnSlugAlreadyExists() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(true);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var result = useCase.execute(validCommand());

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_ALREADY_EXISTS.message());
            verify(productGateway, never()).save(any());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando SKU já estiver em uso")
        void givenDuplicateSku_whenExecute_thenReturnSkuAlreadyExists() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(true);

            final var result = useCase.execute(validCommand());

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_ALREADY_EXISTS.message());
            verify(productGateway, never()).save(any());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Deve acumular ambos os erros quando slug e SKU já estiverem em uso")
        void givenDuplicateSlugAndSku_whenExecute_thenAccumulateBothErrors() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(true);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(true);

            final var result = useCase.execute(validCommand());

            assertTrue(result.isLeft());
            assertEquals(2, result.getLeft().getErrors().size());
            assertError(result.getLeft(), ProductError.SLUG_ALREADY_EXISTS.message());
            assertError(result.getLeft(), ProductError.SKU_ALREADY_EXISTS.message());
            verify(productGateway, never()).save(any());
        }

        @Test
        @DisplayName("Deve ignorar checagem de slug quando slug for nulo")
        void givenNullSlug_whenExecute_thenSkipSlugUniquenessCheck() {
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var cmd = commandWith(null, "CAMISETA-001", "Camiseta", new BigDecimal("99.90"));
            useCase.execute(cmd);

            verify(productGateway, never()).existsBySlug(any());
        }

        @Test
        @DisplayName("Deve ignorar checagem de SKU quando SKU for nulo")
        void givenNullSku_whenExecute_thenSkipSkuUniquenessCheck() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);

            final var cmd = commandWith("camiseta-nike", null, "Camiseta", new BigDecimal("99.90"));
            useCase.execute(cmd);

            verify(productGateway, never()).existsBySku(any());
        }
    }

    // ── validações de campos ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validações de campos")
    class FieldValidations {

        @Test
        @DisplayName("Deve retornar erro quando name for nulo")
        void givenNullName_whenExecute_thenReturnNameEmptyError() {
            mockUniquenessChecks();

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", null, new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_EMPTY.message());
            verify(productGateway, never()).save(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando name for em branco")
        void givenBlankName_whenExecute_thenReturnNameEmptyError() {
            mockUniquenessChecks();

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "   ", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_EMPTY.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando name ultrapassar 300 caracteres")
        void givenNameTooLong_whenExecute_thenReturnNameTooLongError() {
            mockUniquenessChecks();

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "A".repeat(301), new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_TOO_LONG.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando slug for nulo")
        void givenNullSlug_whenExecute_thenReturnSlugEmptyError() {
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var result = useCase.execute(commandWith(null, "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_EMPTY.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando slug for em branco")
        void givenBlankSlug_whenExecute_thenReturnSlugEmptyError() {
            when(productGateway.existsBySlug("   ")).thenReturn(false);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var result = useCase.execute(commandWith("   ", "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_EMPTY.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando slug ultrapassar 350 caracteres")
        void givenSlugTooLong_whenExecute_thenReturnSlugTooLongError() {
            final var longSlug = "a".repeat(351);
            when(productGateway.existsBySlug(longSlug)).thenReturn(false);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var result = useCase.execute(commandWith(longSlug, "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_TOO_LONG.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando slug tiver formato inválido (letras maiúsculas)")
        void givenUpperCaseSlug_whenExecute_thenReturnSlugInvalidFormatError() {
            when(productGateway.existsBySlug("Camiseta-Nike")).thenReturn(false);
            when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);

            final var result = useCase.execute(commandWith("Camiseta-Nike", "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_INVALID_FORMAT.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando SKU for nulo")
        void givenNullSku_whenExecute_thenReturnSkuEmptyError() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);

            final var result = useCase.execute(commandWith("camiseta-nike", null, "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_EMPTY.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando SKU ultrapassar 50 caracteres")
        void givenSkuTooLong_whenExecute_thenReturnSkuTooLongError() {
            final var longSku = "A".repeat(51);
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);
            when(productGateway.existsBySku(longSku)).thenReturn(false);

            final var result = useCase.execute(commandWith("camiseta-nike", longSku, "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_TOO_LONG.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando SKU tiver formato inválido (letras minúsculas)")
        void givenLowerCaseSku_whenExecute_thenReturnSkuInvalidFormatError() {
            when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);
            when(productGateway.existsBySku("camiseta-001")).thenReturn(false);

            final var result = useCase.execute(commandWith("camiseta-nike", "camiseta-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_INVALID_FORMAT.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando price for nulo")
        void givenNullPrice_whenExecute_thenReturnPriceNullError() {
            mockUniquenessChecks();

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "Camiseta Nike", null));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.PRICE_NULL.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando price for negativo")
        void givenNegativePrice_whenExecute_thenReturnPriceNegativeError() {
            mockUniquenessChecks();

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "Camiseta Nike", new BigDecimal("-0.01")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.PRICE_NEGATIVE.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando lowStockThreshold for negativo")
        void givenNegativeLowStockThreshold_whenExecute_thenReturnThresholdNegativeError() {
            mockUniquenessChecks();

            final var cmd = new CreateProductCommand(
                    null, null, "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    -1, null, null, null, null, List.of()
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.LOW_STOCK_THRESHOLD_NEGATIVE.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando shortDescription ultrapassar 500 caracteres")
        void givenShortDescriptionTooLong_whenExecute_thenReturnShortDescriptionTooLongError() {
            mockUniquenessChecks();

            final var cmd = new CreateProductCommand(
                    null, null, "Camiseta Nike", "camiseta-nike", null, "X".repeat(501),
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, null, null, null, null, List.of()
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SHORT_DESCRIPTION_TOO_LONG.message());
        }
    }

    // ── dimensões ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Dimensões")
    class Dimensions {

        @Test
        @DisplayName("Deve retornar erro quando peso for negativo")
        void givenNegativeWeight_whenExecute_thenReturnLeftWithError() {
            mockUniquenessChecks();

            final var cmd = new CreateProductCommand(
                    null, null, "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, new BigDecimal("-1"), null, null, null, List.of()
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
            verify(productGateway, never()).save(any());
        }

        @Test
        @DisplayName("Deve aceitar dimensões nulas (todas opcionais)")
        void givenAllNullDimensions_whenExecute_thenSucceed() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            assertNull(result.get().weight());
            assertNull(result.get().width());
            assertNull(result.get().height());
            assertNull(result.get().depth());
        }
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Falha na transação")
    class TransactionFailure {

        @Test
        @DisplayName("Deve retornar erro quando gateway lançar exceção ao salvar")
        void givenGatewayException_whenExecute_thenReturnLeftNotification() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenThrow(new RuntimeException("db error"));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Não deve publicar eventos quando save falhar")
        void givenGatewayException_whenExecute_thenEventsNeverPublished() {
            mockUniquenessChecks();
            when(productGateway.save(any(Product.class))).thenThrow(new RuntimeException("timeout"));

            useCase.execute(validCommand());

            verify(eventPublisher, never()).publishAll(anyList());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void mockUniquenessChecks() {
        when(productGateway.existsBySlug("camiseta-nike")).thenReturn(false);
        when(productGateway.existsBySku("CAMISETA-001")).thenReturn(false);
    }

    private CreateProductCommand validCommand() {
        return new CreateProductCommand(
                null, null,
                "Camiseta Nike", "camiseta-nike",
                "Descrição completa", "Descrição curta",
                "CAMISETA-001",
                new BigDecimal("99.90"), null, null,
                5,
                null, null, null, null,
                List.of()
        );
    }

    private CreateProductCommand commandWith(
            final String slug, final String sku, final String name, final BigDecimal price
    ) {
        return new CreateProductCommand(
                null, null, name, slug, null, null,
                sku, price, null, null, 0,
                null, null, null, null, List.of()
        );
    }

    private CreateProductCommand commandWithImages(final List<CreateProductCommand.ImageEntry> images) {
        return new CreateProductCommand(
                null, null,
                "Camiseta Nike", "camiseta-nike", null, null,
                "CAMISETA-001", new BigDecimal("99.90"), null, null,
                5, null, null, null, null,
                images
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
