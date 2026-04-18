package com.btree.shared.enums;

/**
 * Status de um pedido.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.order_status}.
 */
public enum OrderStatus {
    PENDING("Pendente"),
    CONFIRMED("Confirmado"),
    PROCESSING("Em processamento"),
    SHIPPED("Enviado"),
    DELIVERED("Entregue"),
    CANCELLED("Cancelado"),
    REFUNDED("Reembolsado"),
    PARTIALLY_REFUNDED("Parcialmente reembolsado");

    private final String description;

    OrderStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
