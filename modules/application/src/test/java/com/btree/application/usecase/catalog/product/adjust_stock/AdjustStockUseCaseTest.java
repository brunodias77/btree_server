package com.btree.application.usecase.catalog.product.adjust_stock;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.error.StockMovementError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjustStock use case")
class AdjustStockUseCaseTest extends UseCaseTest {

    private static final String PRODUCT_ID = UUID.randomUUID().toString();

    @Mock ProductGateway       productGateway;
    @Mock StockMovementGateway stockMovementGateway;
    @Mock DomainEventPublisher eventPublisher;

    AdjustStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AdjustStockUseCase(
                productGateway, stockMovementGateway, eventPublisher,
                new ImmediateTransactionManager()
        );
    }

    // ── caminho feliz ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve adicionar estoque (delta positivo) e retornar output correto")
        void givenPositiveDelta_whenExecute_thenAddStockAndReturnOutput() {
            final var product = activeProduct(10);
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(product));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = commandWith(5, "ADJUSTMENT");
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(PRODUCT_ID, output.productId());
            assertEquals(5,          output.delta());
            assertEquals(15,         output.quantityAfter());
            assertEquals("ADJUSTMENT", output.movementType());
        }

        @Test
        @DisplayName("Deve deduzir estoque (delta negativo) e retornar output correto")
        void givenNegativeDelta_whenExecute_thenDeductStockAndReturnOutput() {
            final var product = activeProduct(10);
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(product));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = commandWith(-3, "ADJUSTMENT");
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(-3, output.delta());
            assertEquals(7,  output.quantityAfter());
        }

        @Test
        @DisplayName("Deve persistir produto e movimento exatamente uma vez")
        void givenValidCommand_whenExecute_thenGatewaysCalledOnce() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(commandWith(5, "ADJUSTMENT"));

            verify(productGateway, times(1)).update(any(Product.class));
            verify(stockMovementGateway, times(1)).save(any(StockMovement.class));
        }

        @Test
        @DisplayName("Deve publicar eventos do aggregate e evento de ajuste após persistência")
        void givenValidCommand_whenExecute_thenPublishEvents() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(commandWith(5, "ADJUSTMENT"));

            verify(eventPublisher, times(1)).publishAll(any());
            verify(eventPublisher, times(1)).publish(any());
        }

        @Test
        @DisplayName("Deve aceitar referenceId nulo sem erro")
        void givenNullReferenceId_whenExecute_thenSucceed() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "ADJUSTMENT", null, null, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
        }

        @Test
        @DisplayName("Deve aceitar referenceId em branco (tratado como nulo) sem erro")
        void givenBlankReferenceId_whenExecute_thenSucceed() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "ADJUSTMENT", null, "  ", null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
        }

        @Test
        @DisplayName("Deve aceitar referenceId como UUID válido sem erro")
        void givenValidReferenceId_whenExecute_thenSucceed() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

            final var validUuid = UUID.randomUUID().toString();
            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "ADJUSTMENT", null, validUuid, "NF");
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
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

            assertThrows(NotFoundException.class, () -> useCase.execute(commandWith(5, "ADJUSTMENT")));

            verify(productGateway, never()).update(any());
            verify(stockMovementGateway, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }

    // ── validações de negócio ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validações de negócio")
    class BusinessValidations {

        @Test
        @DisplayName("Deve retornar erro quando delta for zero")
        void givenDeltaZero_whenExecute_thenReturnQuantityZeroError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var result = useCase.execute(commandWith(0, "ADJUSTMENT"));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), StockMovementError.QUANTITY_ZERO.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando movementType for nulo")
        void givenNullMovementType_whenExecute_thenReturnMovementTypeNullError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, null, null, null, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), StockMovementError.MOVEMENT_TYPE_NULL.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando movementType for em branco")
        void givenBlankMovementType_whenExecute_thenReturnMovementTypeNullError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "  ", null, null, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), StockMovementError.MOVEMENT_TYPE_NULL.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando movementType for valor desconhecido")
        void givenUnknownMovementType_whenExecute_thenReturnMovementTypeNullError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "INVALID_TYPE", null, null, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), StockMovementError.MOVEMENT_TYPE_NULL.message());
        }

        @Test
        @DisplayName("Deve retornar erro quando estoque for insuficiente para dedução")
        void givenDeltaExceedsCurrentStock_whenExecute_thenReturnInsufficientStockError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(5)));

            final var result = useCase.execute(commandWith(-10, "ADJUSTMENT"));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), StockMovementError.INSUFFICIENT_STOCK.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve retornar erro quando produto estiver soft-deleted")
        void givenDeletedProduct_whenExecute_thenReturnCannotModifyDeletedProductError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(deletedProduct()));

            final var result = useCase.execute(commandWith(5, "ADJUSTMENT"));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.CANNOT_MODIFY_DELETED_PRODUCT.message());
            verify(productGateway, never()).update(any());
        }

        @Test
        @DisplayName("Deve acumular delta zero e movementType nulo como dois erros distintos")
        void givenDeltaZeroAndNullMovementType_whenExecute_thenAccumulateBothErrors() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 0, null, null, null, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertEquals(2, result.getLeft().getErrors().size());
            assertError(result.getLeft(), StockMovementError.QUANTITY_ZERO.message());
            assertError(result.getLeft(), StockMovementError.MOVEMENT_TYPE_NULL.message());
        }
    }

    // ── referenceId inválido ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceId inválido")
    class InvalidReferenceId {

        @Test
        @DisplayName("Deve retornar Left quando referenceId não for um UUID válido")
        void givenInvalidReferenceIdUuid_whenExecute_thenReturnLeftWithError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));

            final var cmd = new AdjustStockCommand(PRODUCT_ID, 5, "ADJUSTMENT", null, "not-a-uuid", null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), "'referenceId' não é um UUID válido");
            verify(productGateway, never()).update(any());
            verify(stockMovementGateway, never()).save(any());
        }
    }

    // ── falha na transação ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Falha na transação")
    class TransactionFailure {

        @Test
        @DisplayName("Deve retornar Left quando productGateway.update lançar exceção")
        void givenGatewayUpdateThrows_whenExecute_thenReturnLeftNotification() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenThrow(new RuntimeException("db error"));

            final var result = useCase.execute(commandWith(5, "ADJUSTMENT"));

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
        }

        @Test
        @DisplayName("Não deve publicar eventos quando persistência falhar")
        void givenGatewayUpdateThrows_whenExecute_thenEventsNeverPublished() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenThrow(new RuntimeException("timeout"));

            useCase.execute(commandWith(5, "ADJUSTMENT"));

            verify(eventPublisher, never()).publishAll(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("Deve retornar Left quando stockMovementGateway.save lançar exceção")
        void givenMovementGatewaySaveThrows_whenExecute_thenReturnLeftNotification() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(activeProduct(10)));
            when(productGateway.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(stockMovementGateway.save(any(StockMovement.class))).thenThrow(new RuntimeException("constraint violation"));

            final var result = useCase.execute(commandWith(5, "ADJUSTMENT"));

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Produto ativo com a quantidade informada. */
    private Product activeProduct(final int quantity) {
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
                quantity, 5,
                null,
                ProductStatus.ACTIVE,
                false,
                Instant.now(), Instant.now(), null,
                0,
                List.of()
        );
    }

    /** Produto com soft-delete aplicado (deletedAt não nulo). */
    private Product deletedProduct() {
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
                ProductStatus.ACTIVE,
                false,
                Instant.now(), Instant.now(), Instant.now(),
                0,
                List.of()
        );
    }

    private AdjustStockCommand commandWith(final int delta, final String movementType) {
        return new AdjustStockCommand(PRODUCT_ID, delta, movementType, null, null, null);
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
