package com.btree.domain.cart.gateway;


import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.identifier.CartId;
import com.btree.shared.enums.CartStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartGateway {

    Cart save(Cart cart);

    Cart update(Cart cart);

    Optional<Cart> findById(CartId id);

    Optional<Cart> findActiveByUserId(UUID userId);

    Optional<Cart> findActiveBySessionId(String sessionId);

    List<Cart> findByStatus(CartStatus status);

    /**
     * Busca carrinhos ACTIVE cuja {@code expires_at} já passou.
     * Utilizado pelo job {@code ExpireAbandonedCarts}.
     */
    List<Cart> findExpiredActiveCarts();
}
