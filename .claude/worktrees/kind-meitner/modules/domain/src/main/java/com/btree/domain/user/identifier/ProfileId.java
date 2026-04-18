package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class ProfileId extends Identifier {
    private final UUID value;
    private ProfileId(final UUID value) { this.value = Objects.requireNonNull(value); }
    public static ProfileId unique() { return new ProfileId(UUID.randomUUID()); }
    public static ProfileId from(final UUID value) { return new ProfileId(value); }
    public static ProfileId from(final String value) { return new ProfileId(UUID.fromString(value)); }
    @Override public UUID getValue() { return value; }
}
