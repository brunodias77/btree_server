package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.infrastructure.user.entity.UserTokenJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@Transactional
public class UserTokenPostgresGateway implements UserTokenGateway {

    private final UserTokenJpaRepository userTokenJpaRepository;

    public UserTokenPostgresGateway(final UserTokenJpaRepository userTokenJpaRepository) {
        this.userTokenJpaRepository = userTokenJpaRepository;
    }

    @Override
    public UserToken create(final UserToken userToken) {
        return userTokenJpaRepository
                .save(UserTokenJpaEntity.from(userToken))
                .toAggregate();
    }

    @Override
    public UserToken update(final UserToken userToken) {
        final var entity = userTokenJpaRepository.findById(userToken.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(UserToken.class, userToken.getId().getValue()));
        entity.updateFrom(userToken);
        return userTokenJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserToken> findByTokenHash(final String tokenHash) {
        return userTokenJpaRepository
                .findByTokenHash(tokenHash)
                .map(UserTokenJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserToken> findById(final UserTokenId id) {
        return userTokenJpaRepository
                .findById(id.getValue())
                .map(UserTokenJpaEntity::toAggregate);
    }

    @Override
    public int deleteExpired(final int batchSize) {
        return userTokenJpaRepository.deleteExpiredBatch(Instant.now(), batchSize);
    }

    @Override
    public void invalidateActiveByUserIdAndType(final UserId userId, final String tokenType) {
        userTokenJpaRepository.markActiveAsUsedByUserIdAndType(
                userId.getValue(), tokenType, Instant.now()
        );
    }
}
