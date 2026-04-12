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
 * <p>Persiste os eventos na tabela {@code shared.domain_events} dentro da mesma
 * transação do aggregate. Um job assíncrono ({@code ProcessDomainEventsUseCase})
 * processa os eventos pendentes via polling.
 *
 * <p>Substitui o antigo {@code SpringDomainEventPublisher} que publicava eventos
 * sincronamente em memória sem garantia de entrega.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final DomainEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(
            final DomainEventJpaRepository repository,
            final ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(final DomainEvent event) {
        repository.save(toEntity(event));
    }

    @Override
    public void publishAll(final List<? extends DomainEvent> events) {
        final var entities = events.stream()
                .map(this::toEntity)
                .toList();
        repository.saveAll(entities);
    }

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

    private String serializePayload(final DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event payload: " + event.getEventType(), e);
        }
    }

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
