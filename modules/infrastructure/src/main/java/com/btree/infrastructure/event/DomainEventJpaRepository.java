package com.btree.infrastructure.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para a tabela {@code shared.domain_events}.
 *
 * <p>Expõe as queries necessárias para o job de polling do Outbox Pattern:
 * busca de eventos pendentes, reprocessamento de eventos com falha e
 * marcação em lote como processados.
 *
 * <p>A PK composta ({@code id + created_at}) é refletida no tipo genérico
 * {@link DomainEventEntity.DomainEventEntityId}.
 */
public interface DomainEventJpaRepository
        extends JpaRepository<DomainEventEntity, DomainEventEntity.DomainEventEntityId> {

    /**
     * Busca eventos pendentes de processamento (Outbox polling).
     *
     * <p>Retorna apenas eventos cujo {@code processed_at} ainda é {@code NULL},
     * ordenados por {@code created_at ASC} para garantir processamento FIFO
     * (o evento mais antigo é sempre processado primeiro).
     *
     * @param pageable define o limite de registros por lote (ex.: {@code PageRequest.of(0, 100)})
     * @return lista de eventos ainda não processados, do mais antigo ao mais recente
     */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
    List<DomainEventEntity> findPendingEvents(Pageable pageable);

    /**
     * Busca eventos com falha que ainda podem ser reprocessados.
     *
     * <p>Um evento é elegível para reprocessamento quando:
     * <ul>
     *   <li>Ainda não foi processado com sucesso ({@code processed_at IS NULL}).</li>
     *   <li>Já teve ao menos uma tentativa ({@code retry_count > 0}).</li>
     *   <li>Não excedeu o limite máximo de tentativas ({@code retry_count < maxRetries}).</li>
     * </ul>
     *
     * @param maxRetries número máximo de tentativas permitidas antes de abandonar o evento
     * @param pageable   define o tamanho do lote de reprocessamento
     * @return lista de eventos elegíveis para reprocessamento
     */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.processedAt IS NULL AND e.retryCount > 0 AND e.retryCount < :maxRetries ORDER BY e.createdAt ASC")
    List<DomainEventEntity> findRetryableEvents(@Param("maxRetries") int maxRetries, Pageable pageable);

    /**
     * Busca todos os eventos de um aggregate específico em ordem cronológica.
     *
     * <p>Útil para reconstruir a linha do tempo de mudanças de um aggregate
     * (ex.: todos os eventos do {@code User} com ID {@code X}).
     *
     * @param aggregateType nome do tipo do aggregate (ex.: "User", "Order")
     * @param aggregateId   identificador da instância do aggregate
     * @return eventos do aggregate, do mais antigo ao mais recente
     */
    List<DomainEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType, UUID aggregateId
    );

    /**
     * Conta eventos pendentes por módulo — útil para monitoramento e alertas.
     *
     * <p>Permite identificar módulos com acúmulo de eventos não processados,
     * sinalizando possíveis falhas no job de polling ou nos handlers de eventos.
     *
     * @param module nome do módulo (ex.: "users", "orders")
     * @return total de eventos pendentes naquele módulo
     */
    @Query("SELECT COUNT(e) FROM DomainEventEntity e WHERE e.processedAt IS NULL AND e.module = :module")
    long countPendingByModule(@Param("module") String module);

    /**
     * Marca em lote eventos como processados via {@code UPDATE} direto no banco.
     *
     * <p>Mais eficiente do que carregar cada entidade individualmente e chamar
     * {@code save()}, pois resolve tudo em uma única instrução SQL — importante
     * quando o job processa centenas de eventos por ciclo.
     *
     * <p><b>Requer {@code @Modifying}</b> pois altera estado no banco; deve ser
     * chamado dentro de uma transação ativa.
     *
     * @param ids          lista de IDs dos eventos a marcar
     * @param processedAt  timestamp a ser gravado em {@code processed_at}
     * @return número de linhas afetadas pelo {@code UPDATE}
     */
    @Modifying
    @Query("UPDATE DomainEventEntity e SET e.processedAt = :processedAt, e.errorMessage = NULL WHERE e.id IN :ids")
    int markAsProcessed(@Param("ids") List<UUID> ids, @Param("processedAt") Instant processedAt);
}
