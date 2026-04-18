package com.btree.domain.user.identifier;

import com.btree.shared.domain.Identifier;
import java.util.UUID;

public class SessionId extends Identifier {

    private final UUID value;

    private SessionId(UUID value) {
        this.value = value;
    }

    @Override
    public UUID getValue() {
        return value;
    }

    public static SessionId unique() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId from(final String anId) {
        return new SessionId(UUID.fromString(anId));
    }

    public static SessionId from(final UUID anId) {
        return new SessionId(anId);
    }
}
