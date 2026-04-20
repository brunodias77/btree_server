package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.infrastructure.catalog.entity.StockMovementJpaEntity;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class StockMovementPostgresGateway implements StockMovementGateway {

    private final StockMovementJpaRepository stockMovementJpaRepository;

    public StockMovementPostgresGateway(final StockMovementJpaRepository stockMovementJpaRepository) {
        this.stockMovementJpaRepository = stockMovementJpaRepository;
    }

    @Override
    public StockMovement save(final StockMovement movement) {
        return stockMovementJpaRepository
                .save(StockMovementJpaEntity.from(movement))
                .toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<StockMovement> findByProduct(final ProductId productId, final PageRequest pageRequest) {
        final var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), Sort.by(Sort.Direction.DESC, "createdAt")
        );
        final var page = stockMovementJpaRepository.findByProductIdOrderByCreatedAtDesc(
                productId.getValue(), pageable
        );
        return Pagination.of(
                page.getContent().stream().map(StockMovementJpaEntity::toAggregate).toList(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements()
        );
    }
}
