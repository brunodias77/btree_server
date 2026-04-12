package com.btree.infrastructure.event;

import com.btree.shared.event.IntegrationEvent;
import com.btree.shared.event.IntegrationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica integration events via {@link ApplicationEventPublisher} do Spring.
 *
 * <p><b>TODO:</b> substituir por {@code OutboxIntegrationEventPublisher} que persiste
 * o evento na tabela {@code shared.domain_events} para processamento assíncrono
 * pelo job {@code ProcessDomainEventsUseCase}.
 */
@Component
public class SpringIntegrationEventPublisher implements IntegrationEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringIntegrationEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(final IntegrationEvent event) {
        publisher.publishEvent(event);
    }
}

