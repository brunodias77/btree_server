package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class UserNotificationId extends Identifier {
    private final UUID value;
    private UserNotificationId(final UUID value) { this.value = Objects.requireNonNull(value); }
    public static UserNotificationId unique() { return new UserNotificationId(UUID.randomUUID()); }
    public static UserNotificationId from(final UUID value) { return new UserNotificationId(value); }
    public static UserNotificationId from(final String value) { return new UserNotificationId(UUID.fromString(value)); }
    @Override public UUID getValue() { return value; }
}
