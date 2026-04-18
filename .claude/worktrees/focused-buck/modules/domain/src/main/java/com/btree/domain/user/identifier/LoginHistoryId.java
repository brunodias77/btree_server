package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;
import java.util.UUID;

public class LoginHistoryId extends Identifier {

    private final UUID value;

    private LoginHistoryId(UUID value) {
        this.value = value;
    }

    @Override
    public UUID getValue() {
        return value;
    }

    public static LoginHistoryId unique() {
        return new LoginHistoryId(UUID.randomUUID());
    }

    public static LoginHistoryId from(final String anId) {
        return new LoginHistoryId(UUID.fromString(anId));
    }

    public static LoginHistoryId from(final UUID anId) {
        return new LoginHistoryId(anId);
    }
}
