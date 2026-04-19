package com.btree.shared.enums;

/**
 * Status de um reembolso.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.refund_status}.
 */
public enum RefundStatus {
    PENDING("Pendente"),
    PROCESSING("Em processamento"),
    APPROVED("Aprovado"),
    REJECTED("Rejeitado"),
    PROCESSED("Processado"),
    COMPLETED("Concluído"),
    FAILED("Falhou");

    private final String description;

    RefundStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
