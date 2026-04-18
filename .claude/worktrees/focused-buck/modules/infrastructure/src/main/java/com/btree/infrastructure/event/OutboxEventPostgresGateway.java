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
 * <p>É a interface entre o job de polling ({@code ProcessDomainEventsUseCase}) e a tabela
 * {@code shared.domain_events}. Expõe operações de leitura (busca de pendentes/retryable)
 * e escrita (marcar como processado ou falho).
 *
 * <h3>Controle de transações</h3>
 * <p>A classe está anotada com {@code @Transactional} no nível da classe, o que significa
 * que todos os métodos participam de uma transação por padrão (propagação {@code REQUIRED}).
 * Métodos de leitura sobrescrevem isso com {@code @Transactional(readOnly = true)} para
 * permitir otimizações do Hibernate (ex.: desativar dirty-checking e flush automático).
 *
 * <p>Quando chamados de dentro de um {@code TransactionTemplate.execute()} externo,
 * os métodos de escrita <b>participam</b> dessa transação existente — garantindo que
 * a atualização do status do evento e qualquer outra operação do job sejam atômicas.
 */
@Component
@Transactional
public class OutboxEventPostgresGateway implements OutboxEventGateway {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPostgresGateway.class);

    private final DomainEventJpaRepository repository;

    public OutboxEventPostgresGateway(final DomainEventJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Retorna os próximos eventos pendentes de processamento (Outbox polling).
     *
     * <p>Limitado ao tamanho de lote {@code limit} para evitar consultas excessivamente
     * grandes. O job chama este método em ciclos periódicos até não restar eventos pendentes.
     * Os eventos retornados contém apenas os campos necessários para o job rotear para o
     * handler correto ({@link PendingEvent}), sem carregar o payload completo na memória.
     *
     * @param limit número máximo de eventos a retornar por ciclo de polling
     * @return lista de representações leves dos eventos pendentes
     */
    @Override
    @Transactional(readOnly = true)
    public List<PendingEvent> findPending(final int limit) {
        return repository.findPendingEvents(PageRequest.of(0, limit))
                .stream()
                // Projeta apenas os campos necessários para o roteamento,
                // evitando deserializar o payload JSON desnecessariamente.
                .map(e -> new PendingEvent(e.getId(), e.getCreatedAt(), e.getEventType(), e.getModule()))
                .toList();
    }

    /**
     * Retorna eventos que falharam anteriormente e ainda são candidatos a reprocessamento.
     *
     * <p>Um evento é "retryable" quando: já teve ao menos uma tentativa ({@code retry_count > 0}),
     * mas ainda não atingiu o limite máximo de retries ({@code retry_count < maxRetries}).
     * Isso implementa uma política simples de "retry com limite".
     *
     * @param maxRetries número máximo de tentativas permitidas antes de abandonar o evento
     * @param limit      tamanho máximo do lote de reprocessamento
     * @return lista de eventos elegíveis para reprocessamento
     */
    @Override
    @Transactional(readOnly = true)
    public List<PendingEvent> findRetryable(final int maxRetries, final int limit) {
        return repository.findRetryableEvents(maxRetries, PageRequest.of(0, limit))
                .stream()
                .map(e -> new PendingEvent(e.getId(), e.getCreatedAt(), e.getEventType(), e.getModule()))
                .toList();
    }

    /**
     * Marca um evento como processado com sucesso.
     *
     * <p>Carrega a entidade pela PK composta {@code (id, createdAt)} e delega para
     * {@link DomainEventEntity#markAsProcessed()}, que preenche {@code processed_at}
     * e limpa qualquer mensagem de erro de tentativas anteriores.
     *
     * <p>Se o evento não for encontrado (situação anômala que não deve ocorrer em
     * produção), registra um {@code WARN} no log sem lançar exceção — evitar que uma
     * inconsistência de estado impeça o job de continuar processando outros eventos.
     *
     * @param id        UUID do evento
     * @param createdAt instante de criação (necessário para localizar a partição correta)
     */
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

    /**
     * Registra uma falha no processamento de um evento.
     *
     * <p>Carrega a entidade e delega para {@link DomainEventEntity#markAsFailed(String)},
     * que persiste a mensagem de erro e incrementa {@code retry_count}. O evento permanece
     * com {@code processed_at = NULL} e voltará a aparecer nas próximas chamadas de
     * {@link #findRetryable} enquanto não atingir o {@code maxRetries}.
     *
     * @param id        UUID do evento
     * @param createdAt instante de criação (necessário para localizar a partição correta)
     * @param error     descrição do erro ocorrido durante o processamento
     */
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
