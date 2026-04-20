package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando um ajuste manual de estoque é realizado com sucesso.
 *
 * <p>Publicado após a persistência atômica do {@code Product} atualizado e do
 * {@code StockMovement} correspondente.
 */
public class StockAdjustedEvent extends DomainEvent {

    private final String productId;
    private final int delta;
    private final int quantityAfter;
    private final String movementType;

    public StockAdjustedEvent(
            final String productId,
            final int delta,
            final int quantityAfter,
            final String movementType
    ) {
        super();
        this.productId     = productId;
        this.delta         = delta;
        this.quantityAfter = quantityAfter;
        this.movementType  = movementType;
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
        return "stock.adjusted";
    }

    public String getProductId()    { return productId; }
    public int getDelta()           { return delta; }
    public int getQuantityAfter()   { return quantityAfter; }
    public String getMovementType() { return movementType; }
}
