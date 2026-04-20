package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um produto é publicado (status → ACTIVE).
 */
public class ProductPublishedEvent extends DomainEvent {

    private final String productId;
    private final String name;

    public ProductPublishedEvent(final String productId, final String name) {
        super();
        this.productId = productId;
        this.name = name;
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
        return "product.published";
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
}
