package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class NotificationPreferenceId extends Identifier {
    private final UUID value;
    private NotificationPreferenceId(final UUID value) { this.value = Objects.requireNonNull(value); }
    public static NotificationPreferenceId unique() { return new NotificationPreferenceId(UUID.randomUUID()); }
    public static NotificationPreferenceId from(final UUID value) { return new NotificationPreferenceId(value); }
    public static NotificationPreferenceId from(final String value) { return new NotificationPreferenceId(UUID.fromString(value)); }
    @Override public UUID getValue() { return value; }
}
