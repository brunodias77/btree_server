package com.btree.shared.enums;

/**
 * Tipo de cupom de desconto.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.coupon_type}.
 */
public enum CouponType {
    PERCENTAGE("Percentual"),
    FIXED_AMOUNT("Valor fixo"),
    FREE_SHIPPING("Frete grátis"),
    BUY_X_GET_Y("Compre X e leve Y");

    private final String description;

    CouponType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
