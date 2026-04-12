package com.btree.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    /**
     * Verificação de idempotência — retorna {@code true} se o evento já foi processado.
     * Chamado antes de processar cada evento do outbox.
     */
    boolean existsById(UUID id);

    /**
     * Remove registros antigos para evitar crescimento ilimitado da tabela.
     * Tipicamente chamado por um job de limpeza periódica.
     */
    @Query("DELETE FROM ProcessedEventEntity e WHERE e.processedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);

    /**
     * Conta eventos processados por módulo em um período — útil para monitoramento.
     */
    @Query("SELECT COUNT(e) FROM ProcessedEventEntity e WHERE e.module = :module AND e.processedAt >= :since")
    long countByModuleSince(@Param("module") String module, @Param("since") Instant since);
}
