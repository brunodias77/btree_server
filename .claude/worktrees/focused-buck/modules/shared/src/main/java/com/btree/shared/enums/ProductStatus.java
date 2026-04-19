package com.btree.shared.enums;
/**
 * Status de um produto no catálogo.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.product_status}.
 */
public enum ProductStatus {
    DRAFT("Rascunho"),
    ACTIVE("Ativo"),
    INACTIVE("Inativo"),
    PAUSED("Pausado"),
    OUT_OF_STOCK("Sem estoque"),
    DISCONTINUED("Descontinuado"),
    ARCHIVED("Arquivado");

    private final String description;

    ProductStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
