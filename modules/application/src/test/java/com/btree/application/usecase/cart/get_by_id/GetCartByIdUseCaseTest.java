package com.btree.application.usecase.cart.get_by_id;

import com.btree.application.usecase.UseCaseTest;
import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.cart.identifier.CartId;
import com.btree.domain.cart.identifier.CartItemId;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.enums.CartStatus;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.exception.NotFoundException;
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
@DisplayName("GetCartById use case")
class GetCartByIdUseCaseTest extends UseCaseTest {

    private static final UUID   USER_ID    = UUID.randomUUID();
    private static final String USER_ID_STR = USER_ID.toString();
    private static final String SESSION_ID  = "sess-abc123";

    @Mock CartGateway    cartGateway;
    @Mock ProductGateway productGateway;

    GetCartByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCartByIdUseCase(cartGateway, productGateway);
    }

    // ── Caminho feliz — por userId ────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz — por userId")
    class HappyCasesByUserId {

        @Test
        @DisplayName("Deve retornar output com itens quando carrinho existir para o userId")
        void givenUserId_whenCartFound_thenReturnRightWithItems() {
            final var productId = UUID.randomUUID();
            final var product   = activeProduct(productId, new BigDecimal("99.90"));
            final var cart      = cartWithItem(USER_ID, null, productId, 2, new BigDecimal("99.90"));

            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(cart));
            when(productGateway.findAllByIds(any())).thenReturn(List.of(product));

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(CartStatus.ACTIVE.name(), output.status());
            assertEquals(1, output.items().size());
            assertEquals(2, output.items().get(0).quantity());
            assertEquals(new BigDecimal("99.90"), output.items().get(0).unitPrice());
            assertEquals(new BigDecimal("199.80"), output.items().get(0).subtotal());
            assertFalse(output.hasPriceChanges());
        }

        @Test
        @DisplayName("Deve retornar output com carrinho vazio quando não houver itens")
        void givenUserId_whenCartEmpty_thenReturnRightWithNoItems() {
            final var cart = emptyCart(USER_ID, null);

            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(cart));

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            assertTrue(result.isRight());
            final var output = result.get();
            assertTrue(output.items().isEmpty());
            assertEquals(BigDecimal.ZERO, output.subtotal());
            assertEquals(0, output.totalItems());
            verifyNoInteractions(productGateway);
        }

        @Test
        @DisplayName("Deve sinalizar priceChanged quando preço atual difere do unitPrice armazenado")
        void givenPriceChanged_whenExecute_thenFlagPriceChanges() {
            final var productId    = UUID.randomUUID();
            final var storedPrice  = new BigDecimal("50.00");
            final var currentPrice = new BigDecimal("75.00");
            final var product      = activeProduct(productId, currentPrice);
            final var cart         = cartWithItem(USER_ID, null, productId, 1, storedPrice);

            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(cart));
            when(productGateway.findAllByIds(any())).thenReturn(List.of(product));

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            assertTrue(result.isRight());
            final var output = result.get();
            assertTrue(output.hasPriceChanges());
            final var item = output.items().get(0);
            assertTrue(item.priceChanged());
            assertEquals(storedPrice,  item.unitPrice());
            assertEquals(currentPrice, item.currentPrice());
        }

        @Test
        @DisplayName("Deve usar 'Produto indisponível' e status UNKNOWN quando produto não existir mais no catálogo")
        void givenDeletedProduct_whenExecute_thenFallbackGracefully() {
            final var productId = UUID.randomUUID();
            final var cart      = cartWithItem(USER_ID, null, productId, 1, new BigDecimal("30.00"));

            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(cart));
            when(productGateway.findAllByIds(any())).thenReturn(List.of());

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            assertTrue(result.isRight());
            final var item = result.get().items().get(0);
            assertEquals("Produto indisponível", item.productName());
            assertEquals("UNKNOWN", item.productStatus());
            assertFalse(item.priceChanged());
        }

        @Test
        @DisplayName("Deve chamar findActiveByUserId exatamente uma vez")
        void givenUserId_whenExecute_thenGatewayCalledOnce() {
            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(emptyCart(USER_ID, null)));

            useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            verify(cartGateway, times(1)).findActiveByUserId(USER_ID);
            verify(cartGateway, never()).findActiveBySessionId(any());
        }
    }

    // ── Caminho feliz — por sessionId ─────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz — por sessionId")
    class HappyCasesBySessionId {

        @Test
        @DisplayName("Deve retornar output quando carrinho guest existir para o sessionId")
        void givenSessionId_whenCartFound_thenReturnRight() {
            when(cartGateway.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.of(emptyCart(null, SESSION_ID)));

            final var result = useCase.execute(new GetCartByIdCommand(null, SESSION_ID));

            assertTrue(result.isRight());
            verify(cartGateway, times(1)).findActiveBySessionId(SESSION_ID);
            verify(cartGateway, never()).findActiveByUserId(any());
        }
    }

    // ── Carrinho não encontrado ────────────────────────────────────────────────

    @Nested
    @DisplayName("Carrinho não encontrado")
    class CartNotFound {

        @Test
        @DisplayName("Deve lançar NotFoundException quando carrinho não existir para userId")
        void givenUserId_whenCartNotFound_thenThrowNotFoundException() {
            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.empty());

            final var ex = assertThrows(
                NotFoundException.class,
                () -> useCase.execute(new GetCartByIdCommand(USER_ID_STR, null))
            );

            assertEquals(CartError.CART_NOT_FOUND.message(), ex.getMessage());
        }

        @Test
        @DisplayName("Deve lançar NotFoundException quando carrinho não existir para sessionId")
        void givenSessionId_whenCartNotFound_thenThrowNotFoundException() {
            when(cartGateway.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            assertThrows(
                NotFoundException.class,
                () -> useCase.execute(new GetCartByIdCommand(null, SESSION_ID))
            );
        }
    }

    // ── Validação de entrada ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validação de entrada")
    class InputValidation {

        @Test
        @DisplayName("Deve retornar Left quando userId e sessionId forem ambos nulos")
        void givenBothNull_whenExecute_thenReturnLeft() {
            final var result = useCase.execute(new GetCartByIdCommand(null, null));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), CartError.INVALID_IDENTIFICATION.message());
            verifyNoInteractions(cartGateway);
        }

        @Test
        @DisplayName("Deve retornar Left quando userId não for UUID válido")
        void givenInvalidUserId_whenExecute_thenReturnLeft() {
            final var result = useCase.execute(new GetCartByIdCommand("nao-e-uuid", null));

            assertTrue(result.isLeft());
            assertError(result.getLeft(), CartError.INVALID_USER_ID.message());
            verifyNoInteractions(cartGateway);
        }

        @Test
        @DisplayName("Deve preferir userId quando ambos userId e sessionId forem fornecidos")
        void givenBothProvided_whenExecute_thenUseUserId() {
            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(emptyCart(USER_ID, null)));

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, SESSION_ID));

            assertTrue(result.isRight());
            verify(cartGateway, times(1)).findActiveByUserId(USER_ID);
            verify(cartGateway, never()).findActiveBySessionId(any());
        }
    }

    // ── Múltiplos itens ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Carrinho com múltiplos itens")
    class MultipleItems {

        @Test
        @DisplayName("Deve retornar todos os itens e subtotal correto com múltiplos produtos")
        void givenMultipleItems_whenExecute_thenReturnAllItemsAndCorrectSubtotal() {
            final var productId1 = UUID.randomUUID();
            final var productId2 = UUID.randomUUID();
            final var price1     = new BigDecimal("10.00");
            final var price2     = new BigDecimal("20.00");

            final var now  = Instant.now();
            final var cartId = CartId.unique();
            final var item1 = CartItem.with(CartItemId.unique(), cartId, productId1, 3, price1, now, now);
            final var item2 = CartItem.with(CartItemId.unique(), cartId, productId2, 1, price2, now, now);
            final var cart  = Cart.with(
                    cartId, USER_ID, null, CartStatus.ACTIVE,
                    null, null, null, now, now, null, 0,
                    List.of(item1, item2)
            );

            final var p1 = activeProduct(productId1, price1);
            final var p2 = activeProduct(productId2, price2);

            when(cartGateway.findActiveByUserId(USER_ID)).thenReturn(Optional.of(cart));
            when(productGateway.findAllByIds(any())).thenReturn(List.of(p1, p2));

            final var result = useCase.execute(new GetCartByIdCommand(USER_ID_STR, null));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals(2, output.items().size());
            assertEquals(4, output.totalItems());
            assertEquals(new BigDecimal("50.00"), output.subtotal());
            assertFalse(output.hasPriceChanges());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cart emptyCart(final UUID userId, final String sessionId) {
        final var now = Instant.now();
        return Cart.with(
                CartId.unique(), userId, sessionId,
                CartStatus.ACTIVE, null, null, null,
                now, now, null, 0, List.of()
        );
    }

    private Cart cartWithItem(
            final UUID userId,
            final String sessionId,
            final UUID productId,
            final int qty,
            final BigDecimal price
    ) {
        final var now    = Instant.now();
        final var cartId = CartId.unique();
        final var item   = CartItem.with(CartItemId.unique(), cartId, productId, qty, price, now, now);
        return Cart.with(
                cartId, userId, sessionId,
                CartStatus.ACTIVE, null, null, null,
                now, now, null, 0, List.of(item)
        );
    }

    private Product activeProduct(final UUID productId, final BigDecimal price) {
        final var now = Instant.now();
        return Product.with(
                ProductId.from(productId),
                null, null,
                "Produto Teste", "produto-teste",
                null, null,
                "SKU-" + productId.toString().substring(0, 8),
                price, null, null,
                10, 2, null,
                ProductStatus.ACTIVE,
                false,
                now, now, null, 0, List.of()
        );
    }

    private void assertError(
            final com.btree.shared.validation.Notification notification,
            final String message
    ) {
        assertTrue(
            notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
            "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
