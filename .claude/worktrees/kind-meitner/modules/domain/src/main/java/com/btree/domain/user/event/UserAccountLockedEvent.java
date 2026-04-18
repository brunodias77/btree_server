package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

import java.time.Instant;

public class UserAccountLockedEvent extends DomainEvent {

    private final String userId;
    private final Instant expiresAt;

    public UserAccountLockedEvent(final String userId, final Instant expiresAt) {
        super();
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.account_locked"; }

    public String getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
