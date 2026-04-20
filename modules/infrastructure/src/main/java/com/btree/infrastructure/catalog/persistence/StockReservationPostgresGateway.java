package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.StockReservation;
import com.btree.domain.catalog.gateway.StockReservationGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockReservationId;
import com.btree.infrastructure.catalog.entity.StockReservationJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class StockReservationPostgresGateway implements StockReservationGateway {

    private final StockReservationJpaRepository stockReservationJpaRepository;

    public StockReservationPostgresGateway(
            final StockReservationJpaRepository stockReservationJpaRepository
    ) {
        this.stockReservationJpaRepository = stockReservationJpaRepository;
    }

    @Override
    public StockReservation save(final StockReservation reservation) {
        return stockReservationJpaRepository
                .save(StockReservationJpaEntity.from(reservation))
                .toAggregate();
    }

    @Override
    public StockReservation update(final StockReservation reservation) {
        final var entity = stockReservationJpaRepository.findById(reservation.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(StockReservation.class, reservation.getId().getValue()));
        entity.updateFrom(reservation);
        return stockReservationJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockReservation> findById(final StockReservationId id) {
        return stockReservationJpaRepository.findById(id.getValue())
                .map(StockReservationJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockReservation> findActiveByOrderId(final UUID orderId) {
        return stockReservationJpaRepository.findActiveByOrderId(orderId)
                .map(StockReservationJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReservation> findExpiredAndActive(final Instant now) {
        return stockReservationJpaRepository.findExpiredAndActive(now).stream()
                .map(StockReservationJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int sumActiveQuantityByProduct(final ProductId productId) {
        return stockReservationJpaRepository.sumActiveQuantityByProductId(
                productId.getValue(), Instant.now()
        );
    }

    @Override
    public void deleteById(final StockReservationId id) {
        stockReservationJpaRepository.deleteById(id.getValue());
    }
}
