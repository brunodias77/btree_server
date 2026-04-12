package com.btree.application.usecase.job.retry_failed_event;

/**
 * Command de entrada para o job {@link RetryFailedEventsJob}.
 *
 * @param batchSize  número máximo de eventos a reprocessar por execução.
 * @param maxRetries limite máximo de tentativas; eventos com {@code retryCount >= maxRetries}
 *                   não são mais reprocessados.
 */
public record RetryFailedEvents(int batchSize, int maxRetries) {

    public RetryFailedEvents {
        if (batchSize <= 0)  throw new IllegalArgumentException("'batchSize' deve ser maior que zero");
        if (maxRetries <= 0) throw new IllegalArgumentException("'maxRetries' deve ser maior que zero");
    }

    /** Factory com valores padrão seguros para uso no scheduler. */
    public static RetryFailedEvents withDefaults() {
        return new RetryFailedEvents(50, 5);
    }
}
