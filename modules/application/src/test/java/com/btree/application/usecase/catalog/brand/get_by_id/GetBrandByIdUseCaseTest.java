package com.btree.application.usecase.catalog.brand.get_by_id;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.error.BrandError;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetBrandById use case")
class GetBrandByIdUseCaseTest extends UseCaseTest {

    private static final String BRAND_ID = UUID.randomUUID().toString();

    @Mock BrandGateway brandGateway;

    GetBrandByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetBrandByIdUseCase(brandGateway);
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar output completo quando marca existir e não estiver deletada")
        void givenExistingBrandId_whenExecute_thenReturnRight() {
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.of(existingBrand()));

            final var result = useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(BRAND_ID,                            output.id());
            assertEquals("Nike",                              output.name());
            assertEquals("nike",                              output.slug());
            assertEquals("Marca esportiva",                   output.description());
            assertEquals("https://cdn.example.com/nike.png", output.logoUrl());
            assertNotNull(output.createdAt());
            assertNotNull(output.updatedAt());
        }

        @Test
        @DisplayName("Deve retornar output com campos opcionais nulos quando marca não tiver description nem logoUrl")
        void givenBrandWithoutOptionalFields_whenExecute_thenMapNullFields() {
            final var brand = existingBrandMinimal();
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.of(brand));

            final var result = useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            assertTrue(result.isRight());
            assertNull(result.get().description());
            assertNull(result.get().logoUrl());
        }

        @Test
        @DisplayName("Deve chamar brandGateway.findById exatamente uma vez")
        void givenExistingBrandId_whenExecute_thenGatewayCalledOnce() {
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.of(existingBrand()));

            useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            verify(brandGateway, times(1)).findById(BrandId.from(BRAND_ID));
        }
    }

    // ── marca não encontrada ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Marca não encontrada")
    class BrandNotFound {

        @Test
        @DisplayName("Deve retornar Left com BRAND_NOT_FOUND quando marca não existir")
        void givenNonExistentBrandId_whenExecute_thenReturnLeft() {
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.empty());

            final var result = useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), BrandError.BRAND_NOT_FOUND.message());
            verify(brandGateway, times(1)).findById(BrandId.from(BRAND_ID));
        }

        @Test
        @DisplayName("Deve retornar Left com BRAND_NOT_FOUND quando marca estiver soft-deletada")
        void givenSoftDeletedBrandId_whenExecute_thenReturnLeft() {
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.of(deletedBrand()));

            final var result = useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), BrandError.BRAND_NOT_FOUND.message());
            verify(brandGateway, never()).existsBySlug(any());
        }

        @Test
        @DisplayName("Deve retornar exatamente um erro quando marca não for encontrada")
        void givenNonExistentBrandId_whenExecute_thenReturnSingleError() {
            when(brandGateway.findById(BrandId.from(BRAND_ID))).thenReturn(Optional.empty());

            final var result = useCase.execute(new GetBrandByIdCommand(BRAND_ID));

            assertTrue(result.isLeft());
            assertEquals(1, result.getLeft().getErrors().size());
        }
    }

    // ── validação do ID ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validação do ID")
    class IdValidation {

        @Test
        @DisplayName("Deve retornar Left com BRAND_NOT_FOUND quando brandId for UUID inválido")
        void givenInvalidUUID_whenExecute_thenReturnLeft() {
            final var result = useCase.execute(new GetBrandByIdCommand("nao-e-um-uuid"));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), BrandError.BRAND_NOT_FOUND.message());
            verifyNoInteractions(brandGateway);
        }

        @Test
        @DisplayName("Deve retornar Left com BRAND_NOT_FOUND quando brandId for em branco")
        void givenBlankBrandId_whenExecute_thenReturnLeft() {
            final var result = useCase.execute(new GetBrandByIdCommand("   "));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), BrandError.BRAND_NOT_FOUND.message());
            verifyNoInteractions(brandGateway);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Brand existingBrand() {
        final var now = Instant.now();
        return Brand.with(
                BrandId.from(BRAND_ID),
                "Nike", "nike",
                "Marca esportiva",
                "https://cdn.example.com/nike.png",
                now, now, null
        );
    }

    private Brand existingBrandMinimal() {
        final var now = Instant.now();
        return Brand.with(BrandId.from(BRAND_ID), "Nike", "nike", null, null, now, now, null);
    }

    private Brand deletedBrand() {
        final var now = Instant.now();
        return Brand.with(BrandId.from(BRAND_ID), "Nike", "nike", null, null, now, now, now);
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
