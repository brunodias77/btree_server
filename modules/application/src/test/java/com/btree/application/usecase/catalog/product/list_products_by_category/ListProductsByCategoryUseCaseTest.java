package com.btree.application.usecase.catalog.product.list_products_by_category;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.enums.ProductStatus;
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
@DisplayName("ListProductsByCategory use case")
class ListProductsByCategoryUseCaseTest extends UseCaseTest {

    private static final String CATEGORY_ID = UUID.randomUUID().toString();
    private static final String PRODUCT_ID  = UUID.randomUUID().toString();

    @Mock ProductGateway  productGateway;
    @Mock CategoryGateway categoryGateway;

    ListProductsByCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListProductsByCategoryUseCase(productGateway, categoryGateway);
    }

    // ── caminho feliz ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar página de produtos quando categoria existir e não estiver deletada")
        void givenValidCategory_whenExecute_thenReturnPaginatedProducts() {
            final var category = activeCategory();
            final var product  = activeProduct();
            @SuppressWarnings("unchecked")
            final Pagination<Product> page = mock(Pagination.class);
            when(page.items()).thenReturn(List.of(product));
            when(page.currentPage()).thenReturn(0);
            when(page.perPage()).thenReturn(10);
            when(page.total()).thenReturn(1L);
            when(page.totalPages()).thenReturn(1);

            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.of(category));
            when(productGateway.findActiveByCategoryId(any(CategoryId.class), any(PageRequest.class)))
                    .thenReturn(page);

            final var result = useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(1,    output.items().size());
            assertEquals(0,    output.page());
            assertEquals(10,   output.size());
            assertEquals(1L,   output.totalElements());
            assertEquals(1,    output.totalPages());
        }

        @Test
        @DisplayName("Deve retornar página vazia quando nenhum produto ativo existir na categoria")
        void givenCategoryWithNoActiveProducts_whenExecute_thenReturnEmptyPage() {
            @SuppressWarnings("unchecked")
            final Pagination<Product> emptyPage = mock(Pagination.class);
            when(emptyPage.items()).thenReturn(List.of());
            when(emptyPage.currentPage()).thenReturn(0);
            when(emptyPage.perPage()).thenReturn(10);
            when(emptyPage.total()).thenReturn(0L);
            when(emptyPage.totalPages()).thenReturn(0);

            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.of(activeCategory()));
            when(productGateway.findActiveByCategoryId(any(CategoryId.class), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            final var result = useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10));

            assertTrue(result.isRight());
            assertTrue(result.get().items().isEmpty());
            assertEquals(0L, result.get().totalElements());
        }

        @Test
        @DisplayName("Deve passar page e size corretos ao gateway")
        void givenPageAndSize_whenExecute_thenDelegateToGatewayWithCorrectPagination() {
            @SuppressWarnings("unchecked")
            final Pagination<Product> page = mock(Pagination.class);
            when(page.items()).thenReturn(List.of());
            when(page.currentPage()).thenReturn(2);
            when(page.perPage()).thenReturn(20);
            when(page.total()).thenReturn(0L);
            when(page.totalPages()).thenReturn(0);

            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.of(activeCategory()));
            when(productGateway.findActiveByCategoryId(any(CategoryId.class), any(PageRequest.class)))
                    .thenReturn(page);

            useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 2, 20));

            verify(productGateway, times(1))
                    .findActiveByCategoryId(any(CategoryId.class), any(PageRequest.class));
        }

        @Test
        @DisplayName("Deve mapear primaryImageUrl do produto no output")
        void givenProductWithNoPrimaryImage_whenExecute_thenPrimaryImageUrlIsNull() {
            final var product = activeProduct(); // produto sem imagens
            @SuppressWarnings("unchecked")
            final Pagination<Product> page = mock(Pagination.class);
            when(page.items()).thenReturn(List.of(product));
            when(page.currentPage()).thenReturn(0);
            when(page.perPage()).thenReturn(10);
            when(page.total()).thenReturn(1L);
            when(page.totalPages()).thenReturn(1);

            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.of(activeCategory()));
            when(productGateway.findActiveByCategoryId(any(CategoryId.class), any(PageRequest.class)))
                    .thenReturn(page);

            final var result = useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10));

            assertTrue(result.isRight());
            assertNull(result.get().items().getFirst().primaryImageUrl());
        }
    }

    // ── categoria não encontrada ───────────────────────────────────────────────

    @Nested
    @DisplayName("Categoria não encontrada")
    class CategoryNotFound {

        @Test
        @DisplayName("Deve lançar NotFoundException quando categoria não existir")
        void givenNonExistentCategoryId_whenExecute_thenThrowNotFoundException() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10)));

            verifyNoInteractions(productGateway);
        }

        @Test
        @DisplayName("Deve lançar NotFoundException quando categoria estiver soft-deletada")
        void givenSoftDeletedCategory_whenExecute_thenThrowNotFoundException() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.of(deletedCategory()));

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10)));

            verifyNoInteractions(productGateway);
        }

        @Test
        @DisplayName("Não deve consultar produtos quando categoria não existir")
        void givenNonExistentCategoryId_whenExecute_thenProductGatewayNeverCalled() {
            when(categoryGateway.findById(CategoryId.from(CATEGORY_ID))).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> useCase.execute(new ListProductsByCategoryCommand(CATEGORY_ID, 0, 10)));

            verify(productGateway, never()).findActiveByCategoryId(any(), any());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Category activeCategory() {
        final var category = mock(Category.class);
        when(category.isDeleted()).thenReturn(false);
        return category;
    }

    private Category deletedCategory() {
        final var category = mock(Category.class);
        when(category.isDeleted()).thenReturn(true);
        return category;
    }

    private Product activeProduct() {
        return Product.with(
                ProductId.from(PRODUCT_ID),
                CategoryId.from(CATEGORY_ID), null,
                "Camiseta Nike",
                "camiseta-nike",
                null,
                "Descrição curta",
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
}
