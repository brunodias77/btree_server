package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando os dados cadastrais de um produto são editados.
 *
 * <p>Persistido no outbox ({@code shared.domain_events}) e processado
 * assincronamente pelo job {@code ProcessDomainEventsUseCase}.
 */
public class ProductUpdatedEvent extends DomainEvent {

    private final String productId;

    public ProductUpdatedEvent(final String productId) {
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
        return "product.updated";
    }

    public String getProductId() { return productId; }
}
