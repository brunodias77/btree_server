package com.btree.application.usecase.catalog.brand.list_all;

import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.domain.catalog.identifier.BrandId;
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
@DisplayName("ListAllBrand use case")
class ListAllBrandUseCaseTest {

    @Mock
    BrandGateway brandGateway;

    ListAllBrandUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAllBrandUseCase(brandGateway);
    }

    @Test
    @DisplayName("Deve retornar lista de marcas ativas")
    void givenActiveBrands_whenExecute_thenReturnAllItems() {
        final var brand1 = buildBrand("Nike", "nike");
        final var brand2 = buildBrand("Adidas", "adidas");
        when(brandGateway.findAll()).thenReturn(List.of(brand1, brand2));

        final var result = useCase.execute(new ListAllBrandCommand());

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(2, output.items().size());
        assertEquals("nike", output.items().get(0).slug());
        assertEquals("adidas", output.items().get(1).slug());
        verify(brandGateway).findAll();
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando nao houver marcas")
    void givenNoBrands_whenExecute_thenReturnEmptyList() {
        when(brandGateway.findAll()).thenReturn(List.of());

        final var result = useCase.execute(new ListAllBrandCommand());

        assertTrue(result.isRight());
        assertTrue(result.get().items().isEmpty());
        verify(brandGateway).findAll();
    }

    @Test
    @DisplayName("Deve mapear todos os campos da marca no item de saida")
    void givenBrand_whenExecute_thenMapAllFields() {
        final var now = Instant.parse("2026-04-19T10:00:00Z");
        final var brand = Brand.with(
                BrandId.unique(),
                "Nike",
                "nike",
                "Marca esportiva",
                "https://cdn.example.com/nike.png",
                now, now, null
        );
        when(brandGateway.findAll()).thenReturn(List.of(brand));

        final var result = useCase.execute(new ListAllBrandCommand());

        assertTrue(result.isRight());
        final var item = result.get().items().get(0);
        assertEquals(brand.getId().getValue().toString(), item.id());
        assertEquals("Nike", item.name());
        assertEquals("nike", item.slug());
        assertEquals("Marca esportiva", item.description());
        assertEquals("https://cdn.example.com/nike.png", item.logoUrl());
        assertEquals(now, item.createdAt());
        assertEquals(now, item.updatedAt());
    }

    private Brand buildBrand(final String name, final String slug) {
        final var now = Instant.now();
        return Brand.with(BrandId.unique(), name, slug, null, null, now, now, null);
    }
}
