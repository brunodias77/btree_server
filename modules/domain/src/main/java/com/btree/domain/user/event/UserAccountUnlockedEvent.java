package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserAccountUnlockedEvent extends DomainEvent {

    private final String userId;

    public UserAccountUnlockedEvent(final String userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.account_unlocked"; }

    public String getUserId() { return userId; }
}
