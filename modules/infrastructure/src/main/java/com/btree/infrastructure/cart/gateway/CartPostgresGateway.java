package com.btree.infrastructure.cart.gateway;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.cart.identifier.CartId;
import com.btree.infrastructure.cart.entity.CartJpaEntity;
import com.btree.infrastructure.cart.repository.CartJpaRepository;
import com.btree.shared.enums.CartStatus;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class CartPostgresGateway implements CartGateway {

    private final CartJpaRepository cartJpaRepository;

    public CartPostgresGateway(final CartJpaRepository cartJpaRepository) {
        this.cartJpaRepository = cartJpaRepository;
    }

    @Override
    public Cart save(final Cart cart) {
        return cartJpaRepository
                .save(CartJpaEntity.from(cart))
                .toAggregate();
    }

    @Override
    public Cart update(final Cart cart) {
        final var entity = cartJpaRepository.findById(cart.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Cart.class, cart.getId().getValue()));
        entity.updateFrom(cart);
        return cartJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findById(final CartId id) {
        return cartJpaRepository.findById(id.getValue())
                .map(CartJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findActiveByUserId(final UUID userId) {
        return cartJpaRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .map(CartJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findActiveBySessionId(final String sessionId) {
        return cartJpaRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
                .map(CartJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cart> findByStatus(final CartStatus status) {
        return cartJpaRepository.findByStatus(status).stream()
                .map(CartJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cart> findExpiredActiveCarts() {
        return cartJpaRepository.findExpiredActiveCarts(Instant.now()).stream()
                .map(CartJpaEntity::toAggregate)
                .toList();
    }
}

