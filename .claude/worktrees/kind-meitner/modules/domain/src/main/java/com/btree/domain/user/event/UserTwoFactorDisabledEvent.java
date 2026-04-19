package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserTwoFactorDisabledEvent extends DomainEvent {

    private final String userId;

    public UserTwoFactorDisabledEvent(final String userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.two_factor_disabled"; }

    public String getUserId() { return userId; }
}
