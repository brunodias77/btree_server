package com.btree.shared.enums;

/**
 * Status de um cupom de desconto.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.coupon_status}.
 */
public enum CouponStatus {
    DRAFT("Rascunho"),
    ACTIVE("Ativo"),
    INACTIVE("Inativo"),
    PAUSED("Pausado"),
    EXPIRED("Expirado"),
    DEPLETED("Esgotado");

    private final String description;

    CouponStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
