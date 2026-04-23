package com.btree.application.usecase.catalog.product.list_stock_movements;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockMovementId;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;
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
@DisplayName("ListStockMovements use case")
class ListStockMovementsUseCaseTest extends UseCaseTest {

    private static final String PRODUCT_ID = UUID.randomUUID().toString();

    @Mock ProductGateway       productGateway;
    @Mock StockMovementGateway stockMovementGateway;

    ListStockMovementsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListStockMovementsUseCase(productGateway, stockMovementGateway);
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar página de movimentações quando produto existir")
        void givenExistingProduct_whenExecute_thenReturnPaginatedMovements() {
            final var movement = aMovement(StockMovementType.IN, 50);
            final Pagination<StockMovement> page = Pagination.of(List.of(movement), 0, 10, 1L);

            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.of(activeProduct()));
            when(stockMovementGateway.findByProduct(any(ProductId.class), any(PageRequest.class)))
                    .thenReturn(page);

            final var result = useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(1,    output.items().size());
            assertEquals(0,    output.page());
            assertEquals(10,   output.size());
            assertEquals(1L,   output.totalElements());
            assertEquals(1,    output.totalPages());
        }

        @Test
        @DisplayName("Deve mapear corretamente os campos de cada MovementItem")
        void givenMovementWithAllFields_whenExecute_thenMapOutputCorrectly() {
            final var refId   = UUID.randomUUID();
            final var movement = StockMovement.with(
                    StockMovementId.unique(),
                    Instant.now(),
                    ProductId.from(PRODUCT_ID),
                    StockMovementType.IN,
                    100,
                    refId,
                    "order",
                    "Reposição de estoque"
            );
            final Pagination<StockMovement> page = Pagination.of(List.of(movement), 0, 10, 1L);

            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.of(activeProduct()));
            when(stockMovementGateway.findByProduct(any(ProductId.class), any(PageRequest.class)))
                    .thenReturn(page);

            final var result = useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10));

            assertTrue(result.isRight());
            final var item = result.get().items().getFirst();
            assertEquals(StockMovementType.IN,      item.movementType());
            assertEquals(100,                        item.quantity());
            assertEquals(refId,                      item.referenceId());
            assertEquals("order",                    item.referenceType());
            assertEquals("Reposição de estoque",     item.notes());
            assertNotNull(item.createdAt());
            assertNotNull(item.id());
        }

        @Test
        @DisplayName("Deve retornar página vazia quando produto não tiver movimentações")
        void givenProductWithNoMovements_whenExecute_thenReturnEmptyPage() {
            final Pagination<StockMovement> emptyPage = Pagination.of(List.of(), 0, 10, 0L);

            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.of(activeProduct()));
            when(stockMovementGateway.findByProduct(any(ProductId.class), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            final var result = useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10));

            assertTrue(result.isRight());
            assertTrue(result.get().items().isEmpty());
            assertEquals(0L, result.get().totalElements());
        }

        @Test
        @DisplayName("Deve delegar ao gateway com os parâmetros de paginação corretos")
        void givenPageAndSize_whenExecute_thenDelegateToGatewayWithCorrectPagination() {
            final Pagination<StockMovement> page = Pagination.of(List.of(), 2, 20, 0L);

            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.of(activeProduct()));
            when(stockMovementGateway.findByProduct(any(ProductId.class), any(PageRequest.class)))
                    .thenReturn(page);

            useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 2, 20));

            verify(stockMovementGateway, times(1))
                    .findByProduct(any(ProductId.class), any(PageRequest.class));
        }
    }

    // ── produto não encontrado ────────────────────────────────────────────────

    @Nested
    @DisplayName("Produto não encontrado")
    class ProductNotFound {

        @Test
        @DisplayName("Deve lançar NotFoundException quando produto não existir")
        void givenNonExistentProductId_whenExecute_thenThrowNotFoundException() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10)));

            verifyNoInteractions(stockMovementGateway);
        }

        @Test
        @DisplayName("Deve lançar NotFoundException quando produto estiver soft-deletado")
        void givenSoftDeletedProduct_whenExecute_thenThrowNotFoundException() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.of(deletedProduct()));

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10)));

            verifyNoInteractions(stockMovementGateway);
        }

        @Test
        @DisplayName("Não deve consultar o gateway de movimentações quando produto não existir")
        void givenNonExistentProductId_whenExecute_thenStockGatewayNeverCalled() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID)))
                    .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListStockMovementsCommand(PRODUCT_ID, 0, 10)));

            verify(stockMovementGateway, never()).findByProduct(any(), any());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Product activeProduct() {
        return Product.with(
                ProductId.from(PRODUCT_ID),
                null, null,
                "Camiseta Nike", "camiseta-nike",
                null, "Descrição curta",
                "CAMISETA-001",
                new BigDecimal("99.90"),
                null, null,
                10, 5,
                null,
                ProductStatus.ACTIVE,
                false,
                Instant.now(), Instant.now(), null,
                0,
                List.of()
        );
    }

    private Product deletedProduct() {
        return Product.with(
                ProductId.from(PRODUCT_ID),
                null, null,
                "Camiseta Nike", "camiseta-nike",
                null, "Descrição curta",
                "CAMISETA-001",
                new BigDecimal("99.90"),
                null, null,
                10, 5,
                null,
                ProductStatus.ACTIVE,
                false,
                Instant.now(), Instant.now(), Instant.now(),  // deletedAt != null
                0,
                List.of()
        );
    }

    private StockMovement aMovement(final StockMovementType type, final int quantity) {
        return StockMovement.with(
                StockMovementId.unique(),
                Instant.now(),
                ProductId.from(PRODUCT_ID),
                type, quantity,
                null, null, null
        );
    }
}
