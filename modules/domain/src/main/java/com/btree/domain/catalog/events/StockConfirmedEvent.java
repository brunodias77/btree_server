package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Evento de domínio disparado quando uma reserva de estoque é confirmada
 * após a confirmação do pedido.
 */
public class StockConfirmedEvent extends DomainEvent {

    private final String reservationId;
    private final String productId;
    private final int quantity;
    private final UUID orderId;

    public StockConfirmedEvent(
            final String reservationId,
            final String productId,
            final int quantity,
            final UUID orderId
    ) {
        super();
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.orderId = orderId;
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
        return "stock.confirmed";
    }

    public String getReservationId() { return reservationId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public UUID getOrderId() { return orderId; }
}
