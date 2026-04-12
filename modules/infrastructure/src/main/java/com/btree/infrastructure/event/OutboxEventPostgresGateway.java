package com.btree.infrastructure.event;

import com.btree.shared.gateway.OutboxEventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementação de {@link OutboxEventGateway} usando JPA e PostgreSQL.
 *
 * <p>Cada método de escrita ({@code markAsProcessed}, {@code markAsFailed}) é
 * anotado individualmente — quando chamados dentro de um {@code TransactionManager.execute()},
 * participam da transação externa (propagação REQUIRED). Quando chamados de forma
 * isolada (ex.: skip por idempotência), criam sua própria transação.
 */
@Component
@Transactional
public class OutboxEventPostgresGateway implements OutboxEventGateway {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPostgresGateway.class);

    private final DomainEventJpaRepository repository;

    public OutboxEventPostgresGateway(final DomainEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingEvent> findPending(final int limit) {
        return repository.findPendingEvents(PageRequest.of(0, limit))
                .stream()
                .map(e -> new PendingEvent(e.getId(), e.getCreatedAt(), e.getEventType(), e.getModule()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingEvent> findRetryable(final int maxRetries, final int limit) {
        return repository.findRetryableEvents(maxRetries, PageRequest.of(0, limit))
                .stream()
                .map(e -> new PendingEvent(e.getId(), e.getCreatedAt(), e.getEventType(), e.getModule()))
                .toList();
    }

    @Override
    public void markAsProcessed(final UUID id, final Instant createdAt) {
        repository.findById(new DomainEventEntity.DomainEventEntityId(id, createdAt))
                .ifPresentOrElse(
                        entity -> {
                            entity.markAsProcessed();
                            repository.save(entity);
                        },
                        () -> log.warn("[OutboxEventGateway] Evento não encontrado para marcar como processado: {}", id)
                );
    }

    @Override
    public void markAsFailed(final UUID id, final Instant createdAt, final String error) {
        repository.findById(new DomainEventEntity.DomainEventEntityId(id, createdAt))
                .ifPresentOrElse(
                        entity -> {
                            entity.markAsFailed(error);
                            repository.save(entity);
                        },
                        () -> log.warn("[OutboxEventGateway] Evento não encontrado para marcar como falho: {}", id)
                );
    }
}

