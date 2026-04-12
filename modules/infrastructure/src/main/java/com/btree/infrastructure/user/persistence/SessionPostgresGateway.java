package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.SessionJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@Transactional
public class SessionPostgresGateway implements SessionGateway {

    private final SessionJpaRepository sessionJpaRepository;

    public SessionPostgresGateway(final SessionJpaRepository sessionJpaRepository) {
        this.sessionJpaRepository = sessionJpaRepository;
    }

    @Override
    public Session create(final Session session) {
        return sessionJpaRepository
                .save(SessionJpaEntity.from(session))
                .toAggregate();
    }

    @Override
    public Session update(final Session session) {
        final var entity = sessionJpaRepository.findById(session.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Session.class, session.getId().getValue()));
        entity.updateFrom(session);
        return sessionJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findByRefreshTokenHash(final String refreshTokenHash) {
        return sessionJpaRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .map(SessionJpaEntity::toAggregate);
    }

    @Override
    public int revokeAllByUserId(final UserId userId) {
        return sessionJpaRepository.revokeAllActiveByUserId(
                userId.getValue(),
                Instant.now()
        );
    }
}
