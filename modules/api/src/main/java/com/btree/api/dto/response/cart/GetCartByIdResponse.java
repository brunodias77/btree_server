package com.btree.api.dto.response.cart;

import com.btree.application.usecase.cart.get_by_id.GetCartByIdOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GetCartByIdResponse(
        String cartId,
        String status,
        String couponCode,
        String shippingMethod,
        BigDecimal subtotal,
        int totalItems,
        boolean hasPriceChanges,
        List<CartItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {

    public record CartItemResponse(
            String cartItemId,
            String productId,
            String productName,
            String productStatus,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal currentPrice,
            boolean priceChanged,
            BigDecimal subtotal
    ) {}

    public static GetCartByIdResponse from(final GetCartByIdOutput output) {
        return new GetCartByIdResponse(
                output.cartId(),
                output.status(),
                output.couponCode(),
                output.shippingMethod(),
                output.subtotal(),
                output.totalItems(),
                output.hasPriceChanges(),
                output.items().stream()
                        .map(i -> new CartItemResponse(
                                i.cartItemId(), i.productId(),
                                i.productName(), i.productStatus(),
                                i.quantity(), i.unitPrice(),
                                i.currentPrice(), i.priceChanged(),
                                i.subtotal()))
                        .toList(),
                output.createdAt(),
                output.updatedAt(),
                output.expiresAt()
        );
    }
}
