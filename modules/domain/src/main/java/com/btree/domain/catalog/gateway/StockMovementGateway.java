package com.btree.domain.catalog.gateway;



import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;

public interface StockMovementGateway {

    StockMovement save(StockMovement movement);

    Pagination<StockMovement> findByProduct(ProductId productId, PageRequest pageRequest);
}
