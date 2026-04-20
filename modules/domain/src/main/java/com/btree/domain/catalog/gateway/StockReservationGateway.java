package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.StockReservation;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationGateway {

    StockReservation save(StockReservation reservation);

    StockReservation update(StockReservation reservation);

    Optional<StockReservation> findById(StockReservationId id);

    Optional<StockReservation> findActiveByOrderId(UUID orderId);

    List<StockReservation> findExpiredAndActive(Instant now);

    int sumActiveQuantityByProduct(ProductId productId);

    void deleteById(StockReservationId id);
}
