package com.btree.infrastructure.persistence;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogJpaRepository
        extends JpaRepository<AuditLogEntity, AuditLogEntity.AuditLogEntityId> {

    /**
     * Histórico de auditoria de uma entidade específica, ordenado do mais recente.
     */
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId
    );

    /**
     * Histórico de ações de um usuário específico com paginação.
     */
    Page<AuditLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Busca por módulo e período — útil para relatórios de auditoria.
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.module = :module AND a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findByModuleAndPeriod(
            @Param("module") String module,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    /**
     * Busca por tipo de ação em uma entidade — ex: todos os DELETEs de User.
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.entityType = :entityType AND a.action = :action ORDER BY a.createdAt DESC")
    List<AuditLogEntity> findByEntityTypeAndAction(
            @Param("entityType") String entityType,
            @Param("action") String action,
            Pageable pageable
    );
}
