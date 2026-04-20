package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um produto é arquivado (status → DISCONTINUED).
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Remoção de carrinhos que contenham este produto</li>
 *   <li>Notificação para usuários que favoritaram o produto</li>
 * </ul>
 */
public class ProductArchivedEvent extends DomainEvent {

    private final String productId;
    private final String sku;

    public ProductArchivedEvent(final String productId, final String sku) {
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
        return "product.archived";
    }

    public String getProductId() { return productId; }
    public String getSku() { return sku; }
}
