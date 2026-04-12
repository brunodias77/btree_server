package com.btree.infrastructure.event;


import com.btree.shared.gateway.ProcessedEventGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementação de {@link ProcessedEventGateway} usando JPA e PostgreSQL.
 *
 * <p>Gerencia a tabela {@code shared.processed_events}, que funciona como
 * registro imutável de idempotência do Outbox Pattern. O job de polling consulta
 * este gateway antes de processar cada evento, garantindo que nenhum evento seja
 * executado mais de uma vez — mesmo após reinicializações ou falhas parciais.
 *
 * <h3>Fluxo de uso pelo job de polling</h3>
 * <ol>
 *   <li>Job lê eventos pendentes via {@link OutboxEventPostgresGateway#findPending}.</li>
 *   <li>Para cada evento, chama {@link #alreadyProcessed(UUID)}: se {@code true},
 *       pula o evento sem nenhum efeito colateral.</li>
 *   <li>Se {@code false}, executa o handler de domínio correspondente.</li>
 *   <li>Após execução bem-sucedida, chama {@link #recordProcessed} para registrar
 *       o "recibo" de entrega e impedir reprocessamentos futuros.</li>
 * </ol>
 */
@Component
@Transactional
public class ProcessedEventPostgresGateway implements ProcessedEventGateway {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPostgresGateway(final ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Verifica se um evento já foi processado com sucesso.
     *
     * <p>Opera como {@code SELECT EXISTS} direto pelo ID — consulta O(1) graças ao
     * índice da PK. Anotado como {@code readOnly = true} para desativar o
     * dirty-checking do Hibernate e sinalizar ao banco que não há intenção de escrita
     * (permite otimizações de replication/routing em ambientes com réplicas de leitura).
     *
     * @param eventId UUID do evento a verificar
     * @return {@code true} se o evento já constar em {@code shared.processed_events}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(final UUID eventId) {
        return repository.existsById(eventId);
    }

    /**
     * Registra um evento como processado com sucesso.
     *
     * <p>A verificação prévia de duplicidade ({@code existsById}) antes do {@code save}
     * evita erros de violação de chave primária em cenários de concorrência onde dois
     * workers poderiam tentar inserir o mesmo evento simultaneamente — apenas o primeiro
     * INSERT vence; o segundo é bloqueado pela checagem.
     *
     * <p>Idealmente, em alta concorrência, esse controle deveria usar um {@code INSERT ... ON CONFLICT DO NOTHING}
     * via query nativa. A implementação atual é adequada para workloads com um único worker.
     *
     * @param eventId   UUID do evento processado
     * @param eventType tipo do evento (ex.: "UserCreated"), para fins de auditoria
     * @param module    módulo de origem (ex.: "users"), para fins de monitoramento
     */
    @Override
    public void recordProcessed(final UUID eventId, final String eventType, final String module) {
        // Checagem defensiva: evita PKException em reprocessamentos concorrentes
        if (!repository.existsById(eventId)) {
            repository.save(ProcessedEventEntity.builder()
                    .id(eventId)
                    .eventType(eventType)
                    .module(module)
                    .build());
            // Nota: processed_at é preenchido automaticamente pelo @PrePersist de ProcessedEventEntity
        }
    }
}
