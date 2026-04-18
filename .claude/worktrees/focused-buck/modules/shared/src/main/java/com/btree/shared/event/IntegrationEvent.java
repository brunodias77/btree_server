package com.btree.shared.event;

import com.btree.shared.util.UuidV7;

import java.time.Instant;

public abstract class IntegrationEvent {

    private final String eventId;
    private final Instant occurredOn;
    private final String source;

    protected IntegrationEvent(final String source) {
        this.eventId = UuidV7.generate().toString();
        this.occurredOn = Instant.now();
        this.source = source;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredOn() {
        return occurredOn;
    }

    public String getSource() {
        return source;
    }

    public abstract String getEventType();
}
