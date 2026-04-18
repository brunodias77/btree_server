package com.btree.shared.enums;

/**
 * Método de envio.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.shipping_method}.
 */
public enum ShippingMethod {
    STANDARD("Padrão"),
    EXPRESS("Expresso"),
    OVERNIGHT("Entrega no dia seguinte"),
    PICKUP("Retirada na loja"),
    FREE("Frete grátis");

    private final String description;

    ShippingMethod(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
