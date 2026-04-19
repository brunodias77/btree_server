package com.btree.shared.enums;

/**
 * Motivo de cancelamento de um pedido.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.cancellation_reason}.
 */
public enum CancellationReason {
    CUSTOMER_REQUEST("Solicitação do cliente"),
    FRAUD("Fraude"),
    OUT_OF_STOCK("Sem estoque"),
    PAYMENT_FAILED("Pagamento falhou"),
    DUPLICATE_ORDER("Pedido duplicado"),
    OTHER("Outro");

    private final String description;

    CancellationReason(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
