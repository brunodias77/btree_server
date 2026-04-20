package com.btree.application.usecase.catalog.product.list_all;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductImageId;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListAllProducts use case")
class ListAllProductsUseCaseTest extends UseCaseTest {

    @Mock
    ProductGateway productGateway;

    ListAllProductsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAllProductsUseCase(productGateway);
    }

    @Test
    @DisplayName("Deve retornar página de produtos quando gateway retornar resultados")
    void givenValidCommand_whenExecute_thenReturnPaginatedProducts() {
        final var p1 = buildProduct("Camiseta Nike", "camiseta-nike", ProductStatus.ACTIVE);
        final var p2 = buildProduct("Tênis Adidas", "tenis-adidas", ProductStatus.INACTIVE);
        final var pagination = Pagination.of(List.of(p1, p2), 0, 20, 2L);
        when(productGateway.findAll(any(PageRequest.class))).thenReturn(pagination);

        final var result = useCase.execute(new ListAllProductsCommand(0, 20));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(2, output.items().size());
        assertEquals(0, output.page());
        assertEquals(20, output.size());
        assertEquals(2L, output.totalElements());
        assertEquals(1, output.totalPages());
    }

    @Test
    @DisplayName("Deve retornar página vazia quando não houver produtos")
    void givenNoProducts_whenExecute_thenReturnEmptyPage() {
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(), 0, 20, 0L));

        final var result = useCase.execute(new ListAllProductsCommand(0, 20));

        assertTrue(result.isRight());
        assertTrue(result.get().items().isEmpty());
        assertEquals(0L, result.get().totalElements());
    }

    @Test
    @DisplayName("Deve repassar page e size corretos ao gateway")
    void givenPageAndSize_whenExecute_thenForwardToGateway() {
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(), 2, 10, 0L));
        final var captor = ArgumentCaptor.forClass(PageRequest.class);

        useCase.execute(new ListAllProductsCommand(2, 10));

        verify(productGateway).findAll(captor.capture());
        assertEquals(2, captor.getValue().page());
        assertEquals(10, captor.getValue().size());
    }

    @Test
    @DisplayName("Deve mapear todos os campos do produto no item de saída")
    void givenProduct_whenExecute_thenMapAllFields() {
        final var now = Instant.parse("2026-04-20T10:00:00Z");
        final var product = Product.with(
                ProductId.unique(),
                CategoryId.unique(),
                null,
                "Camiseta Nike",
                "camiseta-nike",
                "Descrição completa",
                "Descrição curta",
                "SKU-001",
                new BigDecimal("99.90"),
                new BigDecimal("149.90"),
                new BigDecimal("50.00"),
                100,
                10,
                null,
                ProductStatus.ACTIVE,
                true,
                now, now, null,
                0,
                List.of()
        );
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(product), 0, 20, 1L));

        final var result = useCase.execute(new ListAllProductsCommand(0, 20));

        assertTrue(result.isRight());
        final var item = result.get().items().get(0);
        assertEquals(product.getId().getValue().toString(), item.id());
        assertEquals("Camiseta Nike", item.name());
        assertEquals("camiseta-nike", item.slug());
        assertEquals("Descrição curta", item.shortDescription());
        assertEquals("SKU-001", item.sku());
        assertEquals(new BigDecimal("99.90"), item.price());
        assertEquals(new BigDecimal("149.90"), item.compareAtPrice());
        assertEquals(ProductStatus.ACTIVE, item.status());
        assertTrue(item.featured());
        assertNull(item.primaryImageUrl());
    }

    @Test
    @DisplayName("Deve retornar URL da imagem primária quando produto tiver imagem marcada como primary")
    void givenProductWithPrimaryImage_whenExecute_thenReturnPrimaryImageUrl() {
        final var productId = ProductId.unique();
        final var image = ProductImage.with(
                ProductImageId.unique(),
                productId,
                "https://cdn.example.com/img.jpg",
                "alt text",
                0,
                true,
                Instant.now()
        );
        final var product = Product.with(
                productId, CategoryId.unique(), null,
                "Produto", "produto", null, null, "SKU-002",
                new BigDecimal("10.00"), null, null,
                1, 0, null,
                ProductStatus.ACTIVE, false,
                Instant.now(), Instant.now(), null, 0,
                List.of(image)
        );
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(product), 0, 20, 1L));

        final var result = useCase.execute(new ListAllProductsCommand(0, 20));

        assertTrue(result.isRight());
        assertEquals("https://cdn.example.com/img.jpg", result.get().items().get(0).primaryImageUrl());
    }

    @Test
    @DisplayName("Deve retornar primaryImageUrl null quando produto não tiver imagem primária")
    void givenProductWithoutPrimaryImage_whenExecute_thenPrimaryImageUrlIsNull() {
        final var productId = ProductId.unique();
        final var nonPrimaryImage = ProductImage.with(
                ProductImageId.unique(), productId,
                "https://cdn.example.com/img.jpg", "alt", 0, false, Instant.now()
        );
        final var product = Product.with(
                productId, CategoryId.unique(), null,
                "Produto", "produto", null, null, "SKU-003",
                new BigDecimal("10.00"), null, null,
                1, 0, null,
                ProductStatus.ACTIVE, false,
                Instant.now(), Instant.now(), null, 0,
                List.of(nonPrimaryImage)
        );
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(product), 0, 20, 1L));

        final var result = useCase.execute(new ListAllProductsCommand(0, 20));

        assertTrue(result.isRight());
        assertNull(result.get().items().get(0).primaryImageUrl());
    }

    @Test
    @DisplayName("Deve calcular totalPages corretamente")
    void givenTotalElementsAndSize_whenExecute_thenComputeTotalPages() {
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(), 0, 10, 25L));

        final var result = useCase.execute(new ListAllProductsCommand(0, 10));

        assertTrue(result.isRight());
        assertEquals(3, result.get().totalPages());
    }

    @Test
    @DisplayName("Deve delegar ao gateway exatamente uma vez")
    void whenExecute_thenGatewayCalledOnce() {
        when(productGateway.findAll(any(PageRequest.class)))
                .thenReturn(Pagination.of(List.of(), 0, 20, 0L));

        useCase.execute(new ListAllProductsCommand(0, 20));

        verify(productGateway, times(1)).findAll(any(PageRequest.class));
        verifyNoMoreInteractions(productGateway);
    }

    private Product buildProduct(final String name, final String slug, final ProductStatus status) {
        return Product.with(
                ProductId.unique(), CategoryId.unique(), null,
                name, slug, null, null, slug.toUpperCase(),
                new BigDecimal("99.90"), null, null,
                10, 2, null,
                status, false,
                Instant.now(), Instant.now(), null, 0,
                List.of()
        );
    }
}
