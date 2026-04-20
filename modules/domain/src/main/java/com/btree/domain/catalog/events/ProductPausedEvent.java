package com.btree.domain.catalog.events;


import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um produto é pausado (status → INACTIVE).
 */
public class ProductPausedEvent extends DomainEvent {

    private final String productId;

    public ProductPausedEvent(final String productId) {
        super();
        this.productId = productId;
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
        return "product.paused";
    }

    public String getProductId() { return productId; }
}
