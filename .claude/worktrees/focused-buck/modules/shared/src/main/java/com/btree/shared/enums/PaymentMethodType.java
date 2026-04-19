package com.btree.shared.enums;
/**
 * Tipo de método de pagamento.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.payment_method_type}.
 */
public enum PaymentMethodType {
    CREDIT_CARD("Cartão de crédito"),
    DEBIT_CARD("Cartão de débito"),
    PIX("PIX"),
    BOLETO("Boleto bancário"),
    WALLET("Carteira digital"),
    BANK_TRANSFER("Transferência bancária");

    private final String description;

    PaymentMethodType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
