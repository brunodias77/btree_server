package com.btree.shared.enums;
/**
 * Bandeira do cartão de crédito/débito.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.card_brand}.
 */
public enum CardBrand {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    ELO("Elo"),
    HIPERCARD("Hipercard"),
    DINERS("Diners Club"),
    OTHER("Outra");

    private final String description;

    CardBrand(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
