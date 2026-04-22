package com.btree.application.usecase.catalog.product.update;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateProduct use case")
class UpdateProductUseCaseTest extends UseCaseTest {

    private static final String PRODUCT_ID = UUID.randomUUID().toString();

    @Mock ProductGateway         productGateway;
    @Mock DomainEventPublisher   eventPublisher;

    UpdateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProductUseCase(productGateway, eventPublisher, new ImmediateTransactionManager());
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve atualizar produto com mesmos slug/SKU e retornar output correto")
        void givenValidCommandWithSameSlugAndSku_whenExecute_thenReturnUpdatedOutput() {
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(PRODUCT_ID,            output.id());
            assertEquals("Camiseta Nike",        output.name());
            assertEquals("camiseta-nike",        output.slug());
            assertEquals("CAMISETA-001",         output.sku());
            assertEquals(new BigDecimal("99.90"), output.price());
            assertNotNull(output.createdAt());
            assertNotNull(output.updatedAt());
        }

        @Test
        @DisplayName("Deve atualizar produto com novo slug único sem erro")
        void givenCommandWithNewUniqueSlug_whenExecute_thenSucceed() {
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.existsBySlugExcludingId("camiseta-nova", ProductId.from(PRODUCT_ID))).thenReturn(false);
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = commandWith("camiseta-nova", "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90"));
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals("camiseta-nova", result.get().slug());
        }

        @Test
        @DisplayName("Deve atualizar produto com novo SKU único sem erro")
        void givenCommandWithNewUniqueSku_whenExecute_thenSucceed() {
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.existsBySkuExcludingId("NOVO-SKU-001", ProductId.from(PRODUCT_ID))).thenReturn(false);
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = commandWith("camiseta-nike", "NOVO-SKU-001", "Camiseta Nike", new BigDecimal("99.90"));
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals("NOVO-SKU-001", result.get().sku());
        }

        @Test
        @DisplayName("Deve mapear categoryId e brandId corretamente no output")
        void givenCommandWithCategoryAndBrand_whenExecute_thenMapIdsInOutput() {
            final var catId   = UUID.randomUUID().toString();
            final var brandId = UUID.randomUUID().toString();
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new UpdateProductCommand(
                    PRODUCT_ID, catId, brandId,
                    "Camiseta Nike", "camiseta-nike", "Desc", "Desc curta",
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, null, null, null, null, false
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals(catId,   result.get().categoryId());
            assertEquals(brandId, result.get().brandId());
        }

        @Test
        @DisplayName("Deve publicar ProductUpdatedEvent após persistência bem-sucedida")
        void givenValidCommand_whenExecute_thenPublishDomainEvent() {
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            verify(eventPublisher).publishAll(argThat(events -> !events.isEmpty()));
        }

        @Test
        @DisplayName("Deve delegar ao gateway.update exatamente uma vez")
        void givenValidCommand_whenExecute_thenGatewayUpdateCalledOnce() {
            final var existing = existingProduct();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(validCommand());

            verify(productGateway, times(1)).update(any(Product.class));
            verify(productGateway, never()).save(any());
        }

        @Test
        @DisplayName("Não deve chamar existsBySlugExcludingId quando slug não mudou")
        void givenSameSlug_whenExecute_thenSkipSlugUniquenessCheck() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(validCommand()); // same slug "camiseta-nike"

            verify(productGateway, never()).existsBySlugExcludingId(any(), any());
        }

        @Test
        @DisplayName("Não deve chamar existsBySkuExcludingId quando SKU não mudou")
        void givenSameSku_whenExecute_thenSkipSkuUniquenessCheck() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(validCommand()); // same SKU "CAMISETA-001"

            verify(productGateway, never()).existsBySkuExcludingId(any(), any());
        }

        @Test
        @DisplayName("Deve aceitar dimensões nulas no update")
        void givenNullDimensions_whenExecute_thenSucceed() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            assertNull(result.get().weight());
            assertNull(result.get().width());
            assertNull(result.get().height());
            assertNull(result.get().depth());
        }

        @Test
        @DisplayName("Deve atualizar produto com dimensões físicas preenchidas")
        void givenCommandWithDimensions_whenExecute_thenDimensionsMappedInOutput() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new UpdateProductCommand(
                    PRODUCT_ID, null, null,
                    "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5,
                    new BigDecimal("0.3"),   // weight
                    new BigDecimal("50.0"),  // width
                    new BigDecimal("70.0"),  // height
                    new BigDecimal("1.0"),   // depth
                    false
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals(new BigDecimal("0.3"),  result.get().weight());
            assertEquals(new BigDecimal("50.0"), result.get().width());
            assertEquals(new BigDecimal("70.0"), result.get().height());
            assertEquals(new BigDecimal("1.0"),  result.get().depth());
        }

        @Test
        @DisplayName("Deve preservar status, quantity e createdAt originais após update")
        void givenValidCommand_whenExecute_thenPreservesStatusQuantityAndCreatedAt() {
            final var existing = existingProduct();
            final var originalCreatedAt = existing.getCreatedAt();
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existing));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isRight());
            assertEquals(ProductStatus.DRAFT, result.get().status());
            assertEquals(10,                 result.get().quantity());
            assertEquals(originalCreatedAt,  result.get().createdAt());
        }
    }

    // ── unicidade de slug e SKU ───────────────────────────────────────────────

    @Nested
    @DisplayName("Unicidade de slug e SKU")
    class UniquenessChecks {

        @Test
        @DisplayName("Deve retornar erro quando novo slug já pertencer a outro produto")
        void givenDuplicateNewSlug_whenExecute_thenReturnSlugAlreadyExists() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.existsBySlugExcludingId(eq("slug-duplicado"), any())).thenReturn(true);

            final var cmd = commandWith("slug-duplicado", "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90"));
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_ALREADY_EXISTS.message());
            verify(productGateway, never()).update(any());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando novo SKU já pertencer a outro produto")
        void givenDuplicateNewSku_whenExecute_thenReturnSkuAlreadyExists() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.existsBySkuExcludingId(eq("SKU-EXISTENTE"), any())).thenReturn(true);

            final var cmd = commandWith("camiseta-nike", "SKU-EXISTENTE", "Camiseta Nike", new BigDecimal("99.90"));
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_ALREADY_EXISTS.message());
            verify(productGateway, never()).update(any());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Deve acumular ambos os erros quando novo slug e novo SKU já existirem")
        void givenDuplicateNewSlugAndSku_whenExecute_thenAccumulateBothErrors() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.existsBySlugExcludingId(eq("slug-duplicado"), any())).thenReturn(true);
            when(productGateway.existsBySkuExcludingId(eq("SKU-EXISTENTE"), any())).thenReturn(true);

            final var cmd = commandWith("slug-duplicado", "SKU-EXISTENTE", "Camiseta Nike", new BigDecimal("99.90"));
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertEquals(2, result.getLeft().getErrors().size());
            assertError(result.getLeft(), ProductError.SLUG_ALREADY_EXISTS.message());
            assertError(result.getLeft(), ProductError.SKU_ALREADY_EXISTS.message());
            verify(productGateway, never()).update(any());
        }
    }

    // ── produto não encontrado ────────────────────────────────────────────────

    @Nested
    @DisplayName("Produto não encontrado")
    class ProductNotFound {

        @Test
        @DisplayName("Deve lançar NotFoundException quando produto não existir")
        void givenNonExistentProductId_whenExecute_thenThrowNotFoundException() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> useCase.execute(validCommand()));

            verify(productGateway, never()).update(any());
            verify(eventPublisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Não deve chamar cheques de unicidade quando produto não existir")
        void givenNonExistentProductId_whenExecute_thenNoUniquenessChecksCalled() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> useCase.execute(validCommand()));

            verify(productGateway, never()).existsBySlugExcludingId(any(), any());
            verify(productGateway, never()).existsBySkuExcludingId(any(), any());
        }
    }

    // ── validações de campos ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validações de campos via product.update()")
    class FieldValidations {

        @Test
        @DisplayName("Deve retornar erro quando name for nulo")
        void givenNullName_whenExecute_thenReturnNameEmptyError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", null, new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_EMPTY.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando name for em branco")
        void givenBlankName_whenExecute_thenReturnNameEmptyError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "   ", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_EMPTY.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando name ultrapassar 300 caracteres")
        void givenNameTooLong_whenExecute_thenReturnNameTooLongError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "A".repeat(301), new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.NAME_TOO_LONG.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando price for nulo")
        void givenNullPrice_whenExecute_thenReturnPriceNullError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "Camiseta Nike", null));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.PRICE_NULL.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando price for negativo")
        void givenNegativePrice_whenExecute_thenReturnPriceNegativeError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(commandWith("camiseta-nike", "CAMISETA-001", "Camiseta Nike", new BigDecimal("-0.01")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.PRICE_NEGATIVE.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando lowStockThreshold for negativo")
        void givenNegativeLowStockThreshold_whenExecute_thenReturnThresholdNegativeError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var cmd = new UpdateProductCommand(
                    PRODUCT_ID, null, null,
                    "Camiseta Nike", "camiseta-nike", null, null,
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    -1, null, null, null, null, false
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.LOW_STOCK_THRESHOLD_NEGATIVE.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando slug tiver formato inválido (letras maiúsculas)")
        void givenUpperCaseSlug_whenExecute_thenReturnSlugInvalidFormatError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.existsBySlugExcludingId(eq("Slug-Invalido"), any())).thenReturn(false);

            final var result = useCase.execute(commandWith("Slug-Invalido", "CAMISETA-001", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SLUG_INVALID_FORMAT.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando SKU tiver formato inválido (letras minúsculas)")
        void givenLowerCaseSku_whenExecute_thenReturnSkuInvalidFormatError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.existsBySkuExcludingId(eq("sku-invalido"), any())).thenReturn(false);

            final var result = useCase.execute(commandWith("camiseta-nike", "sku-invalido", "Camiseta Nike", new BigDecimal("99.90")));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SKU_INVALID_FORMAT.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando shortDescription ultrapassar 500 caracteres")
        void givenShortDescriptionTooLong_whenExecute_thenReturnShortDescriptionTooLongError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var cmd = new UpdateProductCommand(
                    PRODUCT_ID, null, null,
                    "Camiseta Nike", "camiseta-nike", null, "X".repeat(501),
                    "CAMISETA-001", new BigDecimal("99.90"), null, null,
                    5, null, null, null, null, false
            );
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.SHORT_DESCRIPTION_TOO_LONG.message());
            verify(productGateway, never()).update(any());
        }
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Falha na transação")
    class TransactionFailure {

        @Test
        @DisplayName("Deve retornar Left quando gateway.update lançar exceção")
        void givenGatewayException_whenExecute_thenReturnLeftNotification() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenThrow(new RuntimeException("db error"));

            final var result = useCase.execute(validCommand());

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
        }

        @Test
        @DisplayName("Não deve publicar eventos quando update falhar")
        void givenGatewayException_whenExecute_thenEventsNeverPublished() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));
            when(productGateway.update(any(Product.class))).thenThrow(new RuntimeException("timeout"));

            useCase.execute(validCommand());

            verify(eventPublisher, never()).publishAll(anyList());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Produto existente em DRAFT com slug "camiseta-nike" e SKU "CAMISETA-001". */
    private Product existingProduct() {
        return Product.with(
                ProductId.from(PRODUCT_ID),
                null, null,
                "Camiseta Nike",
                "camiseta-nike",
                "Descrição completa",
                "Descrição curta",
                "CAMISETA-001",
                new BigDecimal("99.90"),
                null, null,
                10, 5,
                null,
                ProductStatus.DRAFT,
                false,
                Instant.now(), Instant.now(), null,
                0,
                List.of()
        );
    }

    /** Comando válido com os mesmos slug e SKU do produto existente (não dispara checagem de unicidade). */
    private UpdateProductCommand validCommand() {
        return new UpdateProductCommand(
                PRODUCT_ID, null, null,
                "Camiseta Nike", "camiseta-nike",
                "Descrição completa", "Descrição curta",
                "CAMISETA-001",
                new BigDecimal("99.90"), null, null,
                5, null, null, null, null,
                false
        );
    }

    private UpdateProductCommand commandWith(
            final String slug, final String sku, final String name, final BigDecimal price
    ) {
        return new UpdateProductCommand(
                PRODUCT_ID, null, null,
                name, slug, null, null,
                sku, price, null, null,
                5, null, null, null, null,
                false
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
