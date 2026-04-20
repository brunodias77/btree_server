package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um novo produto é criado no catálogo.
 *
 * <p>Persistido no outbox ({@code shared.domain_events}) e processado
 * assincronamente pelo job {@code ProcessDomainEventsUseCase}.
 */
public class ProductCreatedEvent extends DomainEvent {

    private final String productId;
    private final String name;
    private final String sku;

    public ProductCreatedEvent(final String productId, final String name, final String sku) {
        super();
        this.productId = productId;
        this.name = name;
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
        return "product.created";
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
}
