package com.btree.application.usecase.cart.get_by_id;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saída para UC-90 — GetCart.
 *
 * <p>Cada item é enriquecido com o preço atual do catálogo ({@code currentPrice})
 * e uma flag {@code priceChanged} que indica discrepância em relação ao
 * {@code unitPrice} armazenado no momento da adição ao carrinho.
 *
 * <p>O {@code subtotal} usa sempre o {@code unitPrice} armazenado — o preço que
 * o usuário viu e aceitou. O {@code currentPrice} é informativo para o frontend
 * exibir alertas de variação.
 */
public record GetCartByIdOutput(
        String cartId,
        String status,
        String couponCode,
        String shippingMethod,
        BigDecimal subtotal,
        int totalItems,
        boolean hasPriceChanges,
        List<CartItemOutput> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public record CartItemOutput(
            String cartItemId,
            String productId,
            String productName,
            String productStatus,
            int quantity,
            BigDecimal unitPrice,       // preço armazenado no momento da adição
            BigDecimal currentPrice,    // preço atual do catálogo
            boolean priceChanged,       // true se currentPrice != unitPrice
            BigDecimal subtotal
    ) {}

    /**
     * Monta o output enriquecendo cada item com o preço atual do produto.
     *
     * @param cart     carrinho ativo
     * @param products mapa {@code productId → Product} para lookup O(1) sem N+1
     */
    public static GetCartByIdOutput from(
            final Cart cart,
            final Map<UUID, Product> products
    ) {
        final var itemOutputs = cart.getItems().stream()
                .map(item -> toItemOutput(item, products.get(item.getProductId())))
                .toList();

        final boolean hasPriceChanges = itemOutputs.stream()
                .anyMatch(CartItemOutput::priceChanged);

        return new GetCartByIdOutput(
                cart.getId().getValue().toString(),
                cart.getStatus().name(),
                cart.getCouponCode(),
                cart.getShippingMethod() != null ? cart.getShippingMethod().name() : null,
                cart.subtotal(),
                cart.totalItems(),
                hasPriceChanges,
                itemOutputs,
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getExpiresAt()
        );
    }

    private static CartItemOutput toItemOutput(final CartItem item, final Product product) {
        final BigDecimal currentPrice  = product != null ? product.getPrice()        : item.getUnitPrice();
        final String     productName   = product != null ? product.getName()          : "Produto indisponível";
        final String     productStatus = product != null ? product.getStatus().name() : "UNKNOWN";
        final boolean    priceChanged  = item.getUnitPrice().compareTo(currentPrice) != 0;

        return new CartItemOutput(
                item.getId().getValue().toString(),
                item.getProductId().toString(),
                productName,
                productStatus,
                item.getQuantity(),
                item.getUnitPrice(),
                currentPrice,
                priceChanged,
                item.subtotal()
        );
    }
}
