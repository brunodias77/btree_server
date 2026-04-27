package com.btree.infrastructure.cart.gateway;

import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.gateway.CartActivityLogGateway;
import com.btree.domain.cart.identifier.CartId;
import com.btree.infrastructure.cart.entity.CartActivityLogJpaEntity;
import com.btree.infrastructure.cart.repository.CartActivityLogJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
public class CartActivityLogPostgresGateway implements CartActivityLogGateway {

    private final CartActivityLogJpaRepository cartActivityLogJpaRepository;

    public CartActivityLogPostgresGateway(final CartActivityLogJpaRepository cartActivityLogJpaRepository) {
        this.cartActivityLogJpaRepository = cartActivityLogJpaRepository;
    }

    @Override
    public CartActivityLog save(final CartActivityLog log) {
        return cartActivityLogJpaRepository
                .save(CartActivityLogJpaEntity.from(log))
                .toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartActivityLog> findByCartId(final CartId cartId) {
        return cartActivityLogJpaRepository
                .findByCartIdOrderByCreatedAtAsc(cartId.getValue())
                .stream()
                .map(CartActivityLogJpaEntity::toAggregate)
                .toList();
    }
}

