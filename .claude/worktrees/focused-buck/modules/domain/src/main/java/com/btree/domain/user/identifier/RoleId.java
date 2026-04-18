package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class RoleId extends Identifier {
    private final UUID value;
    private RoleId(final UUID value) { this.value = Objects.requireNonNull(value); }
    public static RoleId unique() { return new RoleId(UUID.randomUUID()); }
    public static RoleId from(final UUID value) { return new RoleId(value); }
    public static RoleId from(final String value) { return new RoleId(UUID.fromString(value)); }
    @Override public UUID getValue() { return value; }
}
