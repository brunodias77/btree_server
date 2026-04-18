package com.btree.shared.enums;

/**
 * Tipo de transação de pagamento.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.transaction_type}.
 */
public enum TransactionType {
    AUTHORIZATION("Autorização"),
    CAPTURE("Captura"),
    REFUND("Reembolso"),
    CHARGEBACK("Chargeback"),
    VOID("Cancelamento"),
    REVERSAL("Reversão");

    private final String description;

    TransactionType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
