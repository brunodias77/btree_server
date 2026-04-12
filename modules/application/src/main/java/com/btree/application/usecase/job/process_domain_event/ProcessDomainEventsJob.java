package com.btree.application.usecase.job.process_domain_event;


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
 * Job — ProcessDomainEvents [JOB].
 *
 * <p>Consome eventos pendentes do outbox ({@code shared.domain_events}) e
 * os marca como processados, garantindo entrega eventual com idempotência.
 *
 * <p>Algoritmo por execução:
 * <ol>
 *   <li>Busca até {@code batchSize} eventos com {@code processed_at IS NULL}, FIFO.</li>
 *   <li>Para cada evento:
 *     <ul>
 *       <li>Verifica idempotência em {@code shared.processed_events}:
 *           se já presente, apenas marca o outbox como processado e ignora.</li>
 *       <li>Caso contrário: em transação atômica, insere em {@code processed_events}
 *           e define {@code processed_at} no outbox.</li>
 *       <li>Em falha: incrementa {@code retry_count} e registra {@code error_message}
 *           para reprocessamento posterior pelo {@link RetryFailedEventsJob}.</li>
 *     </ul>
 *   </li>
 *   <li>Retorna {@link JobResult} com contadores {@code processed}, {@code skipped}
 *       e {@code failed}.</li>
 * </ol>
 *
 * <p>Cada evento é processado em sua própria transação — uma falha não afeta os demais.
 *
 * <p>Executado a cada 5 segundos via {@code @Scheduled(fixedDelay = 5_000)}.
 *
 * @see ProcessDomainEvents
 * @see JobUseCase
 * @see JobResult
 */
public class ProcessDomainEventsJob implements JobUseCase<ProcessDomainEvents> {

    private static final Logger log = LoggerFactory.getLogger(ProcessDomainEventsJob.class);

    private final OutboxEventGateway outboxEventGateway;
    private final ProcessedEventGateway processedEventGateway;
    private final TransactionManager    transactionManager;

    public ProcessDomainEventsJob(
            final OutboxEventGateway outboxEventGateway,
            final ProcessedEventGateway processedEventGateway,
            final TransactionManager transactionManager
    ) {
        this.outboxEventGateway    = outboxEventGateway;
        this.processedEventGateway = processedEventGateway;
        this.transactionManager    = transactionManager;
    }

    @Override
    public Either<Notification, JobResult> execute(final ProcessDomainEvents command) {

        return Try(() -> {

            final var pending = outboxEventGateway.findPending(command.batchSize());

            if (pending.isEmpty()) {
                log.debug("[ProcessDomainEvents] Nenhum evento pendente.");
                return JobResult.empty();
            }

            int processed = 0, skipped = 0, failed = 0;

            for (final var event : pending) {
                try {
                    if (processedEventGateway.alreadyProcessed(event.id())) {
                        // Idempotência: evento já processado anteriormente — apenas atualiza outbox
                        outboxEventGateway.markAsProcessed(event.id(), event.createdAt());
                        skipped++;
                        log.debug("[ProcessDomainEvents] Evento {} já processado — ignorado.", event.id());
                        continue;
                    }

                    // Atomicamente: registra idempotência + marca outbox como processado
                    transactionManager.execute(() -> {
                        processedEventGateway.recordProcessed(
                                event.id(), event.eventType(), event.module());
                        outboxEventGateway.markAsProcessed(event.id(), event.createdAt());
                        return (Void) null;
                    });

                    processed++;
                    log.debug("[ProcessDomainEvents] Evento {} ({}) processado com sucesso.",
                            event.id(), event.eventType());

                } catch (final Exception e) {
                    try {
                        outboxEventGateway.markAsFailed(event.id(), event.createdAt(), e.getMessage());
                    } catch (final Exception ex) {
                        log.error("[ProcessDomainEvents] Falha ao registrar erro para evento {}: {}",
                                event.id(), ex.getMessage());
                    }
                    failed++;
                    log.warn("[ProcessDomainEvents] Falha ao processar evento {} ({}): {}",
                            event.id(), event.eventType(), e.getMessage());
                }
            }

            if (processed > 0 || failed > 0) {
                log.info("[ProcessDomainEvents] Lote concluído — processados: {}, ignorados: {}, falhas: {}.",
                        processed, skipped, failed);
            }

            return JobResult.of(processed, skipped, failed);

        }).toEither().mapLeft(throwable -> {
            log.error("[ProcessDomainEvents] Falha inesperada no job: {}",
                    throwable.getMessage(), throwable);
            return Notification.create(new Error(throwable.getMessage()));
        });
    }
}
