package com.btree.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para a tabela {@code shared.processed_events}.
 *
 * <p>Fornece as operações necessárias para a gestão do registro de idempotência
 * do Outbox Pattern:
 * <ul>
 *   <li>Verificar se um evento já foi processado antes de executá-lo.</li>
 *   <li>Limpar registros antigos para controlar o crescimento da tabela.</li>
 *   <li>Monitorar a taxa de processamento por módulo.</li>
 * </ul>
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    /**
     * Verificação de idempotência — retorna {@code true} se o evento já foi processado.
     *
     * <p>Este é o método central do mecanismo de idempotência: o job de polling chama
     * {@code existsById(eventId)} antes de despachar qualquer evento para o handler.
     * Se retornar {@code true}, o evento é ignorado imediatamente sem nenhum efeito colateral.
     *
     * <p>Embora o método já exista na interface pai {@link JpaRepository}, ele é redeclarado
     * aqui para documentar explicitamente seu papel no fluxo de idempotência.
     *
     * @param id UUID do evento a verificar
     * @return {@code true} se o evento já constar na tabela de eventos processados
     */
    boolean existsById(UUID id);

    /**
     * Remove registros antigos para evitar crescimento ilimitado da tabela.
     *
     * <p>Deve ser chamado por um job de limpeza periódico (ex.: diariamente ou semanalmente).
     * O critério {@code processed_at < before} permite definir uma política de retenção
     * flexível (ex.: manter apenas os últimos 30 dias).
     *
     * <p><b>Requer {@code @Modifying}</b>: como é um {@code DELETE} em massa, deve ser
     * executado dentro de uma transação. O retorno indica quantas linhas foram removidas,
     * útil para auditoria da limpeza.
     *
     * @param before instante limite; registros com {@code processed_at} anterior a este valor serão removidos
     * @return número de registros deletados
     */
    @Query("DELETE FROM ProcessedEventEntity e WHERE e.processedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);

    /**
     * Conta eventos processados por módulo em um período — útil para monitoramento e dashboards.
     *
     * <p>Permite responder perguntas como "quantos eventos do módulo 'users' foram processados
     * nas últimas 24 horas?", facilitando a detecção de anomalias (queda ou pico inesperado
     * no volume de eventos).
     *
     * @param module nome do módulo (ex.: "users", "orders", "payments")
     * @param since  instante inicial do período de contagem
     * @return total de eventos processados do módulo no período informado
     */
    @Query("SELECT COUNT(e) FROM ProcessedEventEntity e WHERE e.module = :module AND e.processedAt >= :since")
    long countByModuleSince(@Param("module") String module, @Param("since") Instant since);
}
