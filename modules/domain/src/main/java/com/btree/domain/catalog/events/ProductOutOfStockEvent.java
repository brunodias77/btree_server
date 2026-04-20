package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando o estoque de um produto chega a zero
 * e seu status muda para OUT_OF_STOCK.
 */
public class ProductOutOfStockEvent extends DomainEvent {

    private final String productId;
    private final String sku;

    public ProductOutOfStockEvent(final String productId, final String sku) {
        super();
        this.productId = productId;
        this.sku = sku;
    }

    @Override
    public String getAggregateId() {
        return productId;
    }

    @Override
    public String getAggregateType() {
        return "Product";
    }

    @Override
    public String getEventType() {
        return "product.out_of_stock";
    }

    public String getProductId() { return productId; }
    public String getSku() { return sku; }
}
