package com.btree.application.usecase.job.process_domain_event;

/**
 * Command de entrada para o job {@link ProcessDomainEventsJob}.
 *
 * @param batchSize número máximo de eventos a processar por execução.
 *                  Valor padrão recomendado: 100.
 */
public record ProcessDomainEvents(int batchSize) {

    public ProcessDomainEvents {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("'batchSize' deve ser maior que zero");
        }
    }

    /** Factory com valor padrão seguro para uso no scheduler. */
    public static ProcessDomainEvents withDefaultBatch() {
        return new ProcessDomainEvents(100);
    }
}
