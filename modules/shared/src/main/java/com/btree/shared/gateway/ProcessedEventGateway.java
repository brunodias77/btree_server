package com.btree.shared.gateway;



import java.util.UUID;

/**
 * Porta de saída para a tabela de idempotência {@code shared.processed_events}.
 *
 * <p>Antes de processar qualquer evento do outbox, o job verifica se o
 * {@code eventId} já consta nesta tabela. Se sim, o evento é ignorado
 * (já foi processado com sucesso anteriormente — garantia de idempotência).
 */
public interface ProcessedEventGateway {

    /**
     * Retorna {@code true} se o evento já foi processado com sucesso anteriormente.
     * Chamado antes de cada tentativa de processamento.
     */
    boolean alreadyProcessed(UUID eventId);

    /**
     * Registra o evento como processado com sucesso.
     * Deve ser chamado dentro da mesma transação que
     * {@link OutboxEventGateway#markAsProcessed}.
     */
    void recordProcessed(UUID eventId, String eventType, String module);
}
