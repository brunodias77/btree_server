package com.btree.application.usecase.catalog.category;

import com.btree.application.usecase.catalog.category.list_all_categories.ListAllCategoriesUseCase;
import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListAllCategories use case")
class ListAllCategoriesUseCaseTest {

    @Mock
    CategoryGateway categoryGateway;

    ListAllCategoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAllCategoriesUseCase(categoryGateway);
    }

    @Test
    @DisplayName("Deve retornar lista de categorias raiz")
    void givenRootCategories_whenExecute_thenReturnAllRoots() {
        final var cat1 = buildCategory(null, "Esportes", "esportes", 1);
        final var cat2 = buildCategory(null, "Moda", "moda", 2);
        when(categoryGateway.findAll()).thenReturn(List.of(cat1, cat2));

        final var result = useCase.execute(null);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(2, output.size());
        assertEquals("esportes", output.get(0).slug());
        assertEquals("moda", output.get(1).slug());
        verify(categoryGateway).findAll();
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando nao houver categorias")
    void givenNoCategories_whenExecute_thenReturnEmptyList() {
        when(categoryGateway.findAll()).thenReturn(List.of());

        final var result = useCase.execute(null);

        assertTrue(result.isRight());
        assertTrue(result.get().isEmpty());
        verify(categoryGateway).findAll();
    }

    @Test
    @DisplayName("Deve mapear todos os campos da categoria no item de saida")
    void givenCategory_whenExecute_thenMapAllFields() {
        final var now = Instant.parse("2026-04-19T10:00:00Z");
        final var cat = Category.with(
                CategoryId.unique(), null,
                "Esportes", "esportes", "Artigos esportivos",
                "https://cdn.example.com/esportes.png",
                1, true, now, now, null
        );
        when(categoryGateway.findAll()).thenReturn(List.of(cat));

        final var result = useCase.execute(null);

        assertTrue(result.isRight());
        final var item = result.get().get(0);
        assertEquals(cat.getId().getValue().toString(), item.id());
        assertNull(item.parentId());
        assertEquals("Esportes", item.name());
        assertEquals("esportes", item.slug());
        assertEquals("Artigos esportivos", item.description());
        assertEquals("https://cdn.example.com/esportes.png", item.imageUrl());
        assertEquals(1, item.sortOrder());
        assertTrue(item.active());
        assertEquals(now, item.createdAt());
        assertEquals(now, item.updatedAt());
        assertTrue(item.children().isEmpty());
    }

    @Test
    @DisplayName("Deve aninhar categorias filhas dentro da categoria pai")
    void givenParentWithChildren_whenExecute_thenBuildTree() {
        final var parentId = CategoryId.unique();
        final var parent = Category.with(
                parentId, null, "Esportes", "esportes", null, null, 1, true,
                Instant.now(), Instant.now(), null
        );
        final var child1 = Category.with(
                CategoryId.unique(), parentId, "Futebol", "futebol", null, null, 1, true,
                Instant.now(), Instant.now(), null
        );
        final var child2 = Category.with(
                CategoryId.unique(), parentId, "Basquete", "basquete", null, null, 2, true,
                Instant.now(), Instant.now(), null
        );
        when(categoryGateway.findAll()).thenReturn(List.of(parent, child1, child2));

        final var result = useCase.execute(null);

        assertTrue(result.isRight());
        final var roots = result.get();
        assertEquals(1, roots.size());

        final var root = roots.get(0);
        assertEquals("esportes", root.slug());
        assertEquals(2, root.children().size());
        assertEquals("futebol", root.children().get(0).slug());
        assertEquals("basquete", root.children().get(1).slug());
        assertEquals(parentId.getValue().toString(), root.children().get(0).parentId());
    }

    @Test
    @DisplayName("Deve aninhar categorias em multiplos niveis")
    void givenDeepHierarchy_whenExecute_thenBuildNestedTree() {
        final var rootId  = CategoryId.unique();
        final var midId   = CategoryId.unique();
        final var leafId  = CategoryId.unique();

        final var root = Category.with(rootId, null,  "Esportes", "esportes", null, null, 1, true, Instant.now(), Instant.now(), null);
        final var mid  = Category.with(midId,  rootId, "Futebol",  "futebol",  null, null, 1, true, Instant.now(), Instant.now(), null);
        final var leaf = Category.with(leafId, midId,  "Chuteiras","chuteiras",null, null, 1, true, Instant.now(), Instant.now(), null);

        when(categoryGateway.findAll()).thenReturn(List.of(root, mid, leaf));

        final var result = useCase.execute(null);

        assertTrue(result.isRight());
        final var roots = result.get();
        assertEquals(1, roots.size());
        assertEquals(1, roots.get(0).children().size());
        assertEquals(1, roots.get(0).children().get(0).children().size());
        assertEquals("chuteiras", roots.get(0).children().get(0).children().get(0).slug());
    }

    @Test
    @DisplayName("Deve propagar excecao do gateway como Left")
    void givenGatewayThrows_whenExecute_thenReturnLeft() {
        when(categoryGateway.findAll()).thenThrow(new RuntimeException("DB unavailable"));

        assertThrows(RuntimeException.class, () -> useCase.execute(null));
        verify(categoryGateway).findAll();
    }

    private Category buildCategory(
            final CategoryId parentId,
            final String name,
            final String slug,
            final int sortOrder
    ) {
        return Category.with(
                CategoryId.unique(), parentId, name, slug,
                null, null, sortOrder, true,
                Instant.now(), Instant.now(), null
        );
    }
}
