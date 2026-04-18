package com.btree.infrastructure.event;

import com.btree.shared.event.IntegrationEvent;
import com.btree.shared.event.IntegrationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica integration events via {@link ApplicationEventPublisher} do Spring.
 *
 * <p>Integration events diferem de domain events em escopo e destino:
 * <ul>
 *   <li><b>Domain events</b>: comunicação <em>intra-módulo</em>, persistidos no outbox
 *       e processados assincronamente por {@link OutboxDomainEventPublisher}.</li>
 *   <li><b>Integration events</b>: comunicação <em>inter-módulo ou inter-serviços</em>,
 *       despachados pelo event bus do Spring de forma síncrona e em memória.</li>
 * </ul>
 *
 * <p>A publicação via {@link ApplicationEventPublisher} é <b>síncrona</b> e não oferece
 * garantia de entrega (se a JVM falhar logo após o {@code publishEvent}, o evento
 * é perdido). Para cenários críticos, a entrega deve ser migrada para o outbox.
 *
 * <p><b>TODO:</b> Substituir por {@code OutboxIntegrationEventPublisher} que persiste
 * o evento na tabela {@code shared.domain_events} para processamento assíncrono
 * pelo job {@code ProcessDomainEventsUseCase}, garantindo entrega at-least-once.
 */
@Component
public class SpringIntegrationEventPublisher implements IntegrationEventPublisher {

    /** Bus de eventos do Spring — os listeners são descobertos automaticamente por tipo. */
    private final ApplicationEventPublisher publisher;

    public SpringIntegrationEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publica um integration event para os listeners registrados no contexto do Spring.
     *
     * <p>O despacho é síncrono: o método só retorna após todos os listeners terem
     * concluído seu processamento (a menos que o listener use {@code @Async}).
     * Qualquer exceção lançada por um listener propaga-se de volta ao chamador.
     *
     * @param event o integration event a publicar
     */
    @Override
    public void publish(final IntegrationEvent event) {
        publisher.publishEvent(event);
    }
}
