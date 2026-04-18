package com.btree.application.usecase.job.retry_failed_event;


import com.btree.shared.contract.TransactionManager;
import com.btree.shared.gateway.OutboxEventGateway;
import com.btree.shared.gateway.ProcessedEventGateway;
import com.btree.shared.usecase.JobResult;
import com.btree.shared.usecase.JobUseCase;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vavr.API.Try;

/**
 * Job — RetryFailedEvents [JOB].
 *
 * <p>Reprocessa eventos do outbox que falharam anteriormente e ainda estão
 * dentro do limite de tentativas ({@code 0 < retryCount < maxRetries}).
 *
 * <p>Algoritmo idêntico ao {@link ProcessDomainEventsJob}, mas opera
 * exclusivamente sobre eventos retentáveis — não toca em eventos novos
 * (retryCount = 0) nem em eventos que esgotaram tentativas (retryCount >= maxRetries).
 *
 * <p>Executado a cada 60 segundos via {@code @Scheduled(fixedDelay = 60_000)}.
 *
 * @see RetryFailedEvents
 * @see JobUseCase
 * @see JobResult
 */
public class RetryFailedEventsJob implements JobUseCase<RetryFailedEvents> {

    private static final Logger log = LoggerFactory.getLogger(RetryFailedEventsJob.class);

    private final OutboxEventGateway outboxEventGateway;
    private final ProcessedEventGateway processedEventGateway;
    private final TransactionManager    transactionManager;

    public RetryFailedEventsJob(
            final OutboxEventGateway outboxEventGateway,
            final ProcessedEventGateway processedEventGateway,
            final TransactionManager transactionManager
    ) {
        this.outboxEventGateway    = outboxEventGateway;
        this.processedEventGateway = processedEventGateway;
        this.transactionManager    = transactionManager;
    }

    @Override
    public Either<Notification, JobResult> execute(final RetryFailedEvents command) {

        return Try(() -> {

            final var retryable = outboxEventGateway.findRetryable(
                    command.maxRetries(), command.batchSize());

            if (retryable.isEmpty()) {
                log.debug("[RetryFailedEvents] Nenhum evento para retentativa.");
                return JobResult.empty();
            }

            log.info("[RetryFailedEvents] {} evento(s) elegível(is) para retentativa.", retryable.size());

            int retried = 0, skipped = 0, failed = 0;

            for (final var event : retryable) {
                try {
                    if (processedEventGateway.alreadyProcessed(event.id())) {
                        // Processado por outra instância ou execução anterior — apenas sincroniza outbox
                        outboxEventGateway.markAsProcessed(event.id(), event.createdAt());
                        skipped++;
                        log.debug("[RetryFailedEvents] Evento {} já presente em processed_events — sincronizado.",
                                event.id());
                        continue;
                    }

                    transactionManager.execute(() -> {
                        processedEventGateway.recordProcessed(
                                event.id(), event.eventType(), event.module());
                        outboxEventGateway.markAsProcessed(event.id(), event.createdAt());
                        return (Void) null;
                    });

                    retried++;
                    log.info("[RetryFailedEvents] Evento {} ({}) reprocessado com sucesso.",
                            event.id(), event.eventType());

                } catch (final Exception e) {
                    try {
                        outboxEventGateway.markAsFailed(event.id(), event.createdAt(), e.getMessage());
                    } catch (final Exception ex) {
                        log.error("[RetryFailedEvents] Falha ao registrar erro para evento {}: {}",
                                event.id(), ex.getMessage());
                    }
                    failed++;
                    log.warn("[RetryFailedEvents] Nova falha ao reprocessar evento {} ({}): {}",
                            event.id(), event.eventType(), e.getMessage());
                }
            }

            log.info("[RetryFailedEvents] Lote concluído — reprocessados: {}, ignorados: {}, falhas: {}.",
                    retried, skipped, failed);

            return JobResult.of(retried, skipped, failed);

        }).toEither().mapLeft(throwable -> {
            log.error("[RetryFailedEvents] Falha inesperada no job: {}",
                    throwable.getMessage(), throwable);
            return Notification.create(new Error(throwable.getMessage()));
        });
    }
}
