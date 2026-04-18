package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class UserTokenId extends Identifier {

    private final UUID value;

    private UserTokenId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'value' must not be null");
    }

    public static UserTokenId unique() {
        return UserTokenId.from(UUID.randomUUID());
    }

    public static UserTokenId from(final String anId) {
        return new UserTokenId(UUID.fromString(anId));
    }

    public static UserTokenId from(final UUID anId) {
        return new UserTokenId(anId);
    }

    @Override
    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserTokenId that = (UserTokenId) o;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue());
    }
}
