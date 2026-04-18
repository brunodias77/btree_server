package com.btree.shared.event;

public interface IntegrationEventPublisher {
    void publish(IntegrationEvent event);
}
