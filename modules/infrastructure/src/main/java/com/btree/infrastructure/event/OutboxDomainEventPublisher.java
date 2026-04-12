package com.btree.infrastructure.event;

import com.btree.shared.domain.DomainEvent;
import com.btree.shared.event.DomainEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementação do Outbox Pattern para publicação de domain events.
 *
 * <p>Em vez de publicar eventos diretamente em memória (o que não oferece garantia
 * de entrega em caso de falha da JVM), esta classe <b>persiste</b> os eventos na
 * tabela {@code shared.domain_events} dentro da <b>mesma transação</b> que persiste
 * o aggregate. Isso garante atomicidade: ou o aggregate e os eventos são salvos
 * juntos, ou nenhum deles é.
 *
 * <p>Um job assíncrono ({@code ProcessDomainEventsUseCase}) realiza polling na tabela
 * e despacha os eventos pendentes para os handlers registrados.
 *
 * <p>Substitui o antigo {@code SpringDomainEventPublisher} que publicava eventos
 * sincronamente em memória sem garantia de entrega.
 *
 * <h3>Garantias oferecidas</h3>
 * <ul>
 *   <li><b>At-least-once delivery</b>: o job reenvia eventos até que {@code processed_at}
 *       seja preenchido com sucesso.</li>
 *   <li><b>Idempotência</b>: controlada pela tabela {@code shared.processed_events}
 *       (ver {@link ProcessedEventPostgresGateway}).</li>
 *   <li><b>Ordenação</b>: eventos são consumidos em ordem FIFO por {@code created_at}.</li>
 * </ul>
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final DomainEventJpaRepository repository;

    /** ObjectMapper do Spring Boot (configurado com módulos Jackson padrão). */
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(
            final DomainEventJpaRepository repository,
            final ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persiste um único domain event no outbox.
     *
     * <p>Deve ser chamado dentro da transação que persiste o aggregate para garantir
     * que evento e mudança de estado sejam atômicos.
     *
     * @param event o domain event gerado pelo aggregate
     */
    @Override
    public void publish(final DomainEvent event) {
        repository.save(toEntity(event));
    }

    /**
     * Persiste uma lista de domain events no outbox em um único {@code saveAll}.
     *
     * <p>Preferível ao loop de {@link #publish(DomainEvent)} quando o aggregate
     * gerou múltiplos eventos na mesma operação, pois reduz o número de round-trips
     * ao banco de dados.
     *
     * @param events lista de domain events a persistir
     */
    @Override
    public void publishAll(final List<? extends DomainEvent> events) {
        final var entities = events.stream()
                .map(this::toEntity)
                .toList();
        repository.saveAll(entities);
    }

    /**
     * Converte um {@link DomainEvent} de domínio para a entidade JPA de outbox.
     *
     * <p>Todos os campos obrigatórios são extraídos da interface {@link DomainEvent}:
     * o módulo é derivado do {@code aggregateType} via {@link #deriveModule(String)}.
     */
    private DomainEventEntity toEntity(final DomainEvent event) {
        return DomainEventEntity.builder()
                .id(UUID.fromString(event.getEventId()))
                .createdAt(event.getOccurredOn())
                .module(deriveModule(event.getAggregateType()))
                .aggregateType(event.getAggregateType())
                .aggregateId(UUID.fromString(event.getAggregateId()))
                .eventType(event.getEventType())
                .payload(serializePayload(event))
                .build();
    }

    /**
     * Serializa o evento para JSON (payload armazenado como {@code jsonb}).
     *
     * <p>A serialização captura todos os campos do evento concreto via reflexão
     * (Jackson). Em caso de falha, lança {@link IllegalStateException} para que
     * a transação seja revertida — preferível a persistir um payload inválido.
     *
     * @param event evento a serializar
     * @return representação JSON do evento
     * @throws IllegalStateException se a serialização falhar
     */
    private String serializePayload(final DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event payload: " + event.getEventType(), e);
        }
    }

    /**
     * Infere o módulo de negócios a partir do tipo do aggregate.
     *
     * <p>O módulo é usado apenas para monitoramento e roteamento de observabilidade.
     * Aggregates não mapeados recaem no módulo {@code "shared"} como fallback seguro.
     *
     * @param aggregateType nome do tipo do aggregate (ex.: "User", "Product")
     * @return nome do módulo correspondente (ex.: "users", "catalog")
     */
    private static String deriveModule(final String aggregateType) {
        return switch (aggregateType) {
            case "User", "Role", "Session", "Address", "Profile" -> "users";
            case "Product", "Category", "Brand" -> "catalog";
            case "Cart" -> "cart";
            case "Order" -> "orders";
            case "Payment" -> "payments";
            case "Coupon" -> "coupons";
            default -> "shared";
        };
    }
}
