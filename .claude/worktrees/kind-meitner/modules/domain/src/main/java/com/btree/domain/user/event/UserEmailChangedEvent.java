package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserEmailChangedEvent extends DomainEvent {

    private final String userId;
    private final String newEmail;

    public UserEmailChangedEvent(final String userId, final String newEmail) {
        super();
        this.userId = userId;
        this.newEmail = newEmail;
    }

    @Override
    public String getAggregateId() { return userId; }

    @Override
    public String getAggregateType() { return "User"; }

    @Override
    public String getEventType() { return "user.email_changed"; }

    public String getUserId() { return userId; }
    public String getNewEmail() { return newEmail; }
}
