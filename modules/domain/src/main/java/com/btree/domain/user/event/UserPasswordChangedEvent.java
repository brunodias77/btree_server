package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserPasswordChangedEvent extends DomainEvent {

    private final String userId;

    public UserPasswordChangedEvent(final String userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.password_changed"; }

    public String getUserId() { return userId; }
}
