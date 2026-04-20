package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando uma imagem é adicionada ao produto.
 */
public class ProductImageAddedEvent extends DomainEvent {

    private final String productId;
    private final String imageId;

    public ProductImageAddedEvent(final String productId, final String imageId) {
        super();
        this.productId = productId;
        this.imageId   = imageId;
    }

    @Override
    public String getAggregateId() { return productId; }

    @Override
    public String getAggregateType() { return "Product"; }

    @Override
    public String getEventType() { return "product.image.added"; }

    public String getProductId() { return productId; }
    public String getImageId()   { return imageId; }
}
