package com.btree.shared.enums;
/**
 * Escopo de aplicação de um cupom.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.coupon_scope}.
 */
public enum CouponScope {
    ALL("Todos os produtos"),
    CATEGORY("Categoria específica"),
    PRODUCT("Produto específico"),
    USER("Usuário específico");

    private final String description;

    CouponScope(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
