package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class UserId extends Identifier {

    private final UUID value;

    private UserId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'UserId' nao deve ser nulo ");
    }

    public static UserId unique() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(final UUID value) {
        return new UserId(value);
    }

    public static UserId from(final String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public UUID getValue() {
        return value;
    }
}
