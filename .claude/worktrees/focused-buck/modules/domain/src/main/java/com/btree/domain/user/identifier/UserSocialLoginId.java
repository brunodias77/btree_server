package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;

import java.util.Objects;
import java.util.UUID;

public class UserSocialLoginId extends Identifier {

    private final UUID value;

    private UserSocialLoginId(final UUID value) {
        this.value = Objects.requireNonNull(value, "'value' must not be null");
    }

    public static UserSocialLoginId unique() {
        return UserSocialLoginId.from(UUID.randomUUID());
    }

    public static UserSocialLoginId from(final String anId) {
        return new UserSocialLoginId(UUID.fromString(anId));
    }

    public static UserSocialLoginId from(final UUID anId) {
        return new UserSocialLoginId(anId);
    }

    @Override
    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserSocialLoginId that = (UserSocialLoginId) o;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue());
    }
}
