package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio disparado quando uma reserva de estoque é criada.
 *
 * <p>Consumidores esperados:
 * <ul>
 *   <li>Criação de pedido após checkout</li>
 *   <li>Monitoramento de estoque</li>
 * </ul>
 */
public class StockReservedEvent extends DomainEvent {

    private final String reservationId;
    private final String productId;
    private final int quantity;
    private final Instant expiresAt;
    private final UUID orderId;

    public StockReservedEvent(
            final String reservationId,
            final String productId,
            final int quantity,
            final Instant expiresAt,
            final UUID orderId
    ) {
        super();
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
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
        return "stock.reserved";
    }

    public String getReservationId() { return reservationId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getOrderId() { return orderId; }
}
