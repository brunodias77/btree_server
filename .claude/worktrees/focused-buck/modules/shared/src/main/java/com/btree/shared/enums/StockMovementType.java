package com.btree.shared.enums;
/**
 * Tipo de movimentação de estoque.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.stock_movement_type}.
 */
public enum StockMovementType {
    IN("Entrada"),
    OUT("Saída"),
    RESERVE("Reserva"),
    RELEASE("Liberação"),
    ADJUSTMENT("Ajuste"),
    RETURN("Devolução");

    private final String description;

    StockMovementType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
