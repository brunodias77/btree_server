package com.btree.application.usecase.user.job;

/**
 * Comando de entrada para UC-13 — CleanupExpiredTokens.
 *
 * @param batchSize número máximo de tokens a deletar por execução.
 *                  Valor padrão recomendado: 500.
 */
public record CleanupExpiredTokens(int batchSize) {

    public CleanupExpiredTokens {
        if(batchSize <= 0){
            throw new IllegalArgumentException("'batchSize' deve ser maior que zero");
        }
    }

    public static CleanupExpiredTokens withDefaultBatch(){
        return new CleanupExpiredTokens(500);
    }
}
