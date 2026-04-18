package com.btree.shared.enums;
/**
 * Status de um chargeback.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.chargeback_status}.
 */
public enum ChargebackStatus {
    OPENED("Aberto"),
    OPEN("Aberto"),
    UNDER_REVIEW("Em análise"),
    ACCEPTED("Aceito"),
    WON("Ganho"),
    LOST("Perdido"),
    EXPIRED("Expirado");

    private final String description;

    ChargebackStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
