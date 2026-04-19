package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;



/**
 * Evento de domínio disparado quando uma nova marca é criada.
 */
public class BrandCreatedEvent extends DomainEvent {

    private final String brandId;
    private final String name;
    private final String slug;

    public BrandCreatedEvent(final String brandId, final String name, final String slug) {
        super();
        this.brandId = brandId;
        this.name = name;
        this.slug = slug;
    }

    @Override
    public String getAggregateId() {
        return brandId;
    }

    @Override
    public String getAggregateType() {
        return "Brand";
    }

    @Override
    public String getEventType() {
        return "brand.created";
    }

    public String getBrandId() { return brandId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}