package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando uma reserva de estoque é liberada
 * (por expiração, cancelamento ou erro no pedido).
 */
public class StockReleasedEvent extends DomainEvent {

    private final String reservationId;
    private final String productId;
    private final int quantity;

    public StockReleasedEvent(
            final String reservationId,
            final String productId,
            final int quantity
    ) {
        super();
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() {
        return reservationId;
    }

    @Override
    public String getAggregateType() {
        return "StockReservation";
    }

    @Override
    public String getEventType() {
        return "stock.released";
    }

    public String getReservationId() { return reservationId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}
