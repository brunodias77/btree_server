package com.btree.infrastructure.catalog.persistence;

import com.btree.infrastructure.catalog.entity.StockReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {

    @Query("""
            SELECT r FROM StockReservationJpaEntity r
            WHERE r.orderId = :orderId
              AND r.confirmed = false
              AND r.released = false
            """)
    Optional<StockReservationJpaEntity> findActiveByOrderId(@Param("orderId") UUID orderId);

    @Query("""
            SELECT r FROM StockReservationJpaEntity r
            WHERE r.expiresAt <= :now
              AND r.confirmed = false
              AND r.released = false
            """)
    List<StockReservationJpaEntity> findExpiredAndActive(@Param("now") Instant now);

    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0)
            FROM StockReservationJpaEntity r
            WHERE r.productId = :productId
              AND r.confirmed = false
              AND r.released = false
              AND r.expiresAt > :now
            """)
    int sumActiveQuantityByProductId(@Param("productId") UUID productId, @Param("now") Instant now);
}
