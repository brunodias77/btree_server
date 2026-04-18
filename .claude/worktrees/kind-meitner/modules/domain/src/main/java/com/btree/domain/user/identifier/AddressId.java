package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class AddressId extends Identifier {
    private final UUID value;
    private AddressId(final UUID value) { this.value = Objects.requireNonNull(value); }
    public static AddressId unique() { return new AddressId(UUID.randomUUID()); }
    public static AddressId from(final UUID value) { return new AddressId(value); }
    public static AddressId from(final String value) { return new AddressId(UUID.fromString(value)); }
    @Override public UUID getValue() { return value; }
}
