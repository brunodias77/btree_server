package com.btree.infrastructure.event;


import com.btree.shared.gateway.ProcessedEventGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementação de {@link ProcessedEventGateway} usando JPA e PostgreSQL.
 *
 * <p>A tabela {@code shared.processed_events} serve como registro de idempotência:
 * antes de processar qualquer evento do outbox, o job verifica aqui se o evento
 * já foi consumido com sucesso (mesmo após reinicializações ou falhas parciais).
 */
@Component
@Transactional
public class ProcessedEventPostgresGateway implements ProcessedEventGateway {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPostgresGateway(final ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(final UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void recordProcessed(final UUID eventId, final String eventType, final String module) {
        if (!repository.existsById(eventId)) {
            repository.save(ProcessedEventEntity.builder()
                    .id(eventId)
                    .eventType(eventType)
                    .module(module)
                    .build());
        }
    }
}

