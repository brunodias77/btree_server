package com.btree.api.config;

import com.btree.application.usecase.job.clean_expired_tokens.CleanupExpiredTokens;
import com.btree.application.usecase.job.clean_expired_tokens.CleanupExpiredTokensJob;
import com.btree.application.usecase.job.process_domain_event.ProcessDomainEvents;
import com.btree.application.usecase.job.process_domain_event.ProcessDomainEventsJob;
import com.btree.application.usecase.job.retry_failed_event.RetryFailedEvents;
import com.btree.application.usecase.job.retry_failed_event.RetryFailedEventsJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Agenda os 11 jobs periódicos da aplicação via {@code @Scheduled}.
 *
 * <p>Cada método delega para um {@link com.btree.shared.usecase.JobUseCase}
 * localizado em {@code application/usecase/job/<contexto>/}. Nenhuma lógica
 * de negócio reside aqui — apenas a definição de cron/fixedDelay e a chamada
 * ao job.
 *
 * <p>O agendamento é habilitado pela anotação {@code @EnableScheduling} em
 * {@code SchedulingConfig} no módulo {@code infrastructure}.
 */
@Configuration(proxyBeanMethods = false)
public class ScheduledJobsConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsConfig.class);

    private final CleanupExpiredTokensJob cleanupExpiredTokensJob;
    private final ProcessDomainEventsJob  processDomainEventsJob;
    private final RetryFailedEventsJob    retryFailedEventsJob;

    public ScheduledJobsConfig(
            final CleanupExpiredTokensJob cleanupExpiredTokensJob,
            final ProcessDomainEventsJob  processDomainEventsJob,
            final RetryFailedEventsJob    retryFailedEventsJob
    ) {
        this.cleanupExpiredTokensJob = cleanupExpiredTokensJob;
        this.processDomainEventsJob  = processDomainEventsJob;
        this.retryFailedEventsJob    = retryFailedEventsJob;
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    /**
     * UC-13 — CleanupExpiredTokens.
     *
     * <p>Executa diariamente às 03:00 (horário do servidor).
     * Deleta até 500 tokens expirados por execução.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        log.info("[ScheduledJobsConfig] Iniciando limpeza de tokens expirados...");
        cleanupExpiredTokensJob
                .execute(CleanupExpiredTokens.withDefaultBatch())
                .peek(result ->
                    log.info("[ScheduledJobsConfig] CleanupExpiredTokens finalizado: {}", result)
                )
                .peekLeft(notification ->
                    log.error("[ScheduledJobsConfig] Falha no job CleanupExpiredTokens: {}",
                            notification.getErrors())
                );
    }

    // ── Shared / Outbox ───────────────────────────────────────────────────────

    /**
     * UC — ProcessDomainEvents [JOB].
     *
     * <p>Executa a cada 5 segundos. Processa até 100 eventos pendentes do outbox
     * por execução, garantindo idempotência via {@code shared.processed_events}.
     */
    @Scheduled(fixedDelay = 5_000)
    public void processDomainEvents() {
        log.debug("[ScheduledJobsConfig] Processando domain events pendentes...");
        processDomainEventsJob
                .execute(ProcessDomainEvents.withDefaultBatch())
                .peek(result -> {
                    if (result.total() > 0) {
                        log.info("[ScheduledJobsConfig] ProcessDomainEvents finalizado: {}", result);
                    }
                })
                .peekLeft(notification ->
                    log.error("[ScheduledJobsConfig] Falha no job ProcessDomainEvents: {}",
                            notification.getErrors())
                );
    }

    /**
     * UC — RetryFailedEvents [JOB].
     *
     * <p>Executa a cada 60 segundos. Reprocessa até 50 eventos que falharam
     * anteriormente e ainda estão dentro do limite de 5 tentativas.
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryFailedEvents() {
        log.debug("[ScheduledJobsConfig] Reprocessando domain events com falha...");
        retryFailedEventsJob
                .execute(RetryFailedEvents.withDefaults())
                .peek(result -> {
                    if (result.total() > 0) {
                        log.info("[ScheduledJobsConfig] RetryFailedEvents finalizado: {}", result);
                    }
                })
                .peekLeft(notification ->
                    log.error("[ScheduledJobsConfig] Falha no job RetryFailedEvents: {}",
                            notification.getErrors())
                );
    }

    // ── TODO: outros jobs (adicionar conforme implementados) ──────────────────
    // @Scheduled(cron = "0 0 4 * * *")     → CleanupExpiredSessionsJob
    // @Scheduled(fixedDelay = 300_000)      → CleanupExpiredReservationsJob
    // @Scheduled(fixedDelay = 3_600_000)    → ExpireAbandonedCartsJob
    // @Scheduled(fixedDelay = 60_000)       → ProcessPendingWebhooksJob
    // @Scheduled(fixedDelay = 600_000)      → CancelExpiredPaymentsJob
    // @Scheduled(cron = "0 0 2 * * *")      → ExpireCouponsJob
    // @Scheduled(fixedDelay = 900_000)      → CleanupExpiredCouponReservationsJob
    // @Scheduled(fixedDelay = 3_600_000)    → ExpireDepletedCouponsJob
}
