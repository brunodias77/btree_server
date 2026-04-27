package com.btree.infrastructure.cart.gateway;

import com.btree.domain.cart.entity.SavedCart;
import com.btree.domain.cart.gateway.SavedCartGateway;
import com.btree.domain.cart.identifier.SavedCartId;
import com.btree.infrastructure.cart.entity.SavedCartJpaEntity;
import com.btree.infrastructure.cart.repository.SavedCartJpaRepository;
import com.btree.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class SavedCartPostgresGateway implements SavedCartGateway {

    private final SavedCartJpaRepository savedCartJpaRepository;
    private final ObjectMapper objectMapper;

    public SavedCartPostgresGateway(
            final SavedCartJpaRepository savedCartJpaRepository,
            final ObjectMapper objectMapper
    ) {
        this.savedCartJpaRepository = savedCartJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public SavedCart save(final SavedCart savedCart) {
        return savedCartJpaRepository
                .save(SavedCartJpaEntity.from(savedCart, objectMapper))
                .toAggregate(objectMapper);
    }

    @Override
    public SavedCart update(final SavedCart savedCart) {
        final var entity = savedCartJpaRepository.findById(savedCart.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(SavedCart.class, savedCart.getId().getValue()));
        entity.updateFrom(savedCart, objectMapper);
        return savedCartJpaRepository.save(entity).toAggregate(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedCart> findById(final SavedCartId id) {
        return savedCartJpaRepository.findById(id.getValue())
                .map(e -> e.toAggregate(objectMapper));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedCart> findByUserId(final UUID userId) {
        return savedCartJpaRepository.findByUserId(userId).stream()
                .map(e -> e.toAggregate(objectMapper))
                .toList();
    }

    @Override
    public void deleteById(final SavedCartId id) {
        if (!savedCartJpaRepository.existsById(id.getValue())) {
            throw NotFoundException.with(SavedCart.class, id.getValue());
        }
        savedCartJpaRepository.deleteById(id.getValue());
    }
}
