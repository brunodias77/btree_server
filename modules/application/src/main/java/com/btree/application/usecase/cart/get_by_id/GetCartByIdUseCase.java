package com.btree.application.usecase.cart.get_by_id;

import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

public class GetCartByIdUseCase implements QueryUseCase<GetCartByIdCommand, GetCartByIdOutput> {

    private final CartGateway cartGateway;
    private final ProductGateway productGateway;

    public GetCartByIdUseCase(
            final CartGateway cartGateway,
            final ProductGateway productGateway
    ) {
        this.cartGateway    = cartGateway;
        this.productGateway = productGateway;
    }

    @Override
    public Either<Notification, GetCartByIdOutput> execute(GetCartByIdCommand command) {

        // 1. Validar identificação
        if (command.userId() == null && command.sessionId() == null) {
            final var notification = Notification.create();
            notification.append(CartError.INVALID_IDENTIFICATION);
            return Left(notification);
        }

        // 2. Parsear userId
        final UUID userId;
        try {
            userId = command.userId() != null ? UUID.fromString(command.userId()) : null;
        } catch (final IllegalArgumentException e) {
            final var notification = Notification.create();
            notification.append(CartError.INVALID_USER_ID);
            return Left(notification);
        }

        // 3. Localizar carrinho ativo — NotFoundException propaga como 404
        final var cart = (userId != null
                ? cartGateway.findActiveByUserId(userId)
                : cartGateway.findActiveBySessionId(command.sessionId()))
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND.message()));

        // 4. Buscar preços atuais em lote — uma única query ao catálogo
        final Map<UUID, Product> products;
        if (cart.getItems().isEmpty()) {
            products = Map.of();
        } else {
            final var productIds = cart.getItems().stream()
                    .map(item -> ProductId.from(item.getProductId()))
                    .collect(Collectors.toList());

            products = productGateway.findAllByIds(productIds)
                    .stream()
                    .collect(Collectors.toMap(
                            p -> p.getId().getValue(),
                            Function.identity()
                    ));
        }

        // 5. Construir output com enrichment de preço e flag priceChanged
        return Right(GetCartByIdOutput.from(cart, products));
    }
}
