package com.btree.infrastructure.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DomainEventJpaRepository
        extends JpaRepository<DomainEventEntity, DomainEventEntity.DomainEventEntityId> {

    /**
     * Busca eventos pendentes de processamento (Outbox polling).
     * Ordenados por created_at ASC para garantir processamento FIFO.
     */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
    List<DomainEventEntity> findPendingEvents(Pageable pageable);

    /**
     * Busca eventos com falha que ainda podem ser reprocessados.
     */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.processedAt IS NULL AND e.retryCount > 0 AND e.retryCount < :maxRetries ORDER BY e.createdAt ASC")
    List<DomainEventEntity> findRetryableEvents(@Param("maxRetries") int maxRetries, Pageable pageable);

    /**
     * Busca todos os eventos de um aggregate específico.
     */
    List<DomainEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType, UUID aggregateId
    );

    /**
     * Conta eventos pendentes por módulo — útil para monitoramento.
     */
    @Query("SELECT COUNT(e) FROM DomainEventEntity e WHERE e.processedAt IS NULL AND e.module = :module")
    long countPendingByModule(@Param("module") String module);

    /**
     * Marca em lote eventos como processados (update direto no banco para eficiência).
     */
    @Modifying
    @Query("UPDATE DomainEventEntity e SET e.processedAt = :processedAt, e.errorMessage = NULL WHERE e.id IN :ids")
    int markAsProcessed(@Param("ids") List<UUID> ids, @Param("processedAt") Instant processedAt);
}
