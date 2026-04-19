package com.btree.shared.contract;

import java.util.function.Supplier;

/**
 * Porta para execução de lógica dentro de uma transação.
 * Mantém domain e application livres de @Transactional do Spring.
 * Implementação: SpringTransactionManager em infrastructure.
 */
public interface TransactionManager {

    /**
     * Executa o bloco dentro de uma transação, retornando um resultado.
     */
    <T> T execute(Supplier<T> action);

    /**
     * Executa o bloco dentro de uma transação sem retorno.
     */
    void executeVoid(Runnable action);
}
