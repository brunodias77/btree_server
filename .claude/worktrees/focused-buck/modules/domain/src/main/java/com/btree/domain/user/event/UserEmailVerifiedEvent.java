package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserEmailVerifiedEvent extends DomainEvent {

    private final String userId;

    public UserEmailVerifiedEvent(final String userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.email_verified"; }

    public String getUserId() { return userId; }
}
