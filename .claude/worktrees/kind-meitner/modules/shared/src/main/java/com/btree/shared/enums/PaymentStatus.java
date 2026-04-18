package com.btree.shared.enums;

/**
 * Status de um pagamento.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.payment_status}.
 */
public enum PaymentStatus {
    PENDING("Pendente"),
    AUTHORIZED("Autorizado"),
    CAPTURED("Capturado"),
    FAILED("Falhou"),
    CANCELLED("Cancelado"),
    REFUNDED("Reembolsado"),
    PARTIALLY_REFUNDED("Parcialmente reembolsado"),
    CHARGEBACK("Chargeback");

    private final String description;

    PaymentStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
