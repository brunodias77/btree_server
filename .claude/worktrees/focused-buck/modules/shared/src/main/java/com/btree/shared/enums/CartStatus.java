package com.btree.shared.enums;
/**
 * Status do carrinho de compras.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.cart_status}.
 */
public enum CartStatus {
    ACTIVE("Ativo"),
    CONVERTED("Convertido"),
    ABANDONED("Abandonado"),
    EXPIRED("Expirado");

    private final String description;

    CartStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
