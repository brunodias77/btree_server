package com.btree.domain.user.gateway;



import com.btree.domain.user.entity.Session;
import com.btree.domain.user.identifier.UserId;

import java.util.Optional;

public interface SessionGateway {
    Session create(Session session);
    Session update(Session session);
    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);
    int revokeAllByUserId(UserId userId);
}
