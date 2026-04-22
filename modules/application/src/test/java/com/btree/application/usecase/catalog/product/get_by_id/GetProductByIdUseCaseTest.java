package com.btree.application.usecase.catalog.product.get_by_id;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.enums.ProductStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetProductById use case")
class GetProductByIdUseCaseTest extends UseCaseTest {

    private static final String PRODUCT_ID = UUID.randomUUID().toString();

    @Mock ProductGateway productGateway;

    GetProductByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProductByIdUseCase(productGateway);
    }

    // ── caminho feliz ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar output completo quando produto existir")
        void givenExistingProductId_whenExecute_thenReturnRight() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(PRODUCT_ID,              output.id());
            assertEquals("Camiseta Nike",          output.name());
            assertEquals("camiseta-nike",          output.slug());
            assertEquals("CAMISETA-001",           output.sku());
            assertEquals(new BigDecimal("99.90"),  output.price());
            assertEquals(10,                        output.quantity());
            assertEquals(5,                         output.lowStockThreshold());
            assertEquals(ProductStatus.DRAFT,       output.status());
            assertFalse(output.featured());
            assertTrue(output.images().isEmpty());
            assertNotNull(output.createdAt());
            assertNotNull(output.updatedAt());
        }

        @Test
        @DisplayName("Deve mapear categoryId e brandId nulos corretamente no output")
        void givenProductWithNullCategoryAndBrand_whenExecute_thenMapNullIds() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            assertTrue(result.isRight());
            assertNull(result.get().categoryId());
            assertNull(result.get().brandId());
        }

        @Test
        @DisplayName("Deve mapear dimensões nulas no output quando produto não tiver dimensões")
        void givenProductWithNullDimensions_whenExecute_thenMapNullDimensions() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            final var result = useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            assertTrue(result.isRight());
            assertNull(result.get().weight());
            assertNull(result.get().width());
            assertNull(result.get().height());
            assertNull(result.get().depth());
        }

        @Test
        @DisplayName("Deve chamar productGateway.findById exatamente uma vez")
        void givenExistingProductId_whenExecute_thenGatewayCalledOnce() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.of(existingProduct()));

            useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            verify(productGateway, times(1)).findById(ProductId.from(PRODUCT_ID));
        }
    }

    // ── produto não encontrado ────────────────────────────────────────────────

    @Nested
    @DisplayName("Produto não encontrado")
    class ProductNotFound {

        @Test
        @DisplayName("Deve retornar Left com PRODUCT_NOT_FOUND quando produto não existir")
        void givenNonExistentProductId_whenExecute_thenReturnLeftWithProductNotFound() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.empty());

            final var result = useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), ProductError.PRODUCT_NOT_FOUND.message());
        }

        @Test
        @DisplayName("Deve retornar exatamente um erro quando produto não for encontrado")
        void givenNonExistentProductId_whenExecute_thenReturnSingleError() {
            when(productGateway.findById(ProductId.from(PRODUCT_ID))).thenReturn(Optional.empty());

            final var result = useCase.execute(new GetProductByIdCommand(PRODUCT_ID));

            assertTrue(result.isLeft());
            assertEquals(1, result.getLeft().getErrors().size());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
