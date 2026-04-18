package com.btree.shared.domain;

import com.btree.shared.util.UuidV7;

import java.time.Instant;

public abstract class DomainEvent {

    private final String eventId;
    private final Instant occurredOn;

    protected DomainEvent() {
        this.eventId = UuidV7.generate().toString();
        this.occurredOn = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredOn() {
        return occurredOn;
    }

    public abstract String getAggregateId();

    public abstract String getAggregateType();

    public abstract String getEventType();
}
