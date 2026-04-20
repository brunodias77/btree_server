package com.btree.infrastructure.catalog.persistence;

import com.btree.infrastructure.catalog.entity.StockMovementJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementJpaRepository
        extends JpaRepository<StockMovementJpaEntity, StockMovementJpaEntity.StockMovementPk> {

    Page<StockMovementJpaEntity> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
