package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.UserSocialLogin;
import com.btree.domain.user.gateway.UserSocialLoginGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.UserSocialLoginJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class UserSocialLoginPostgresGateway implements UserSocialLoginGateway {

    private final UserSocialLoginJpaRepository repository;

    public UserSocialLoginPostgresGateway(final UserSocialLoginJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserSocialLogin create(final UserSocialLogin userSocialLogin) {
        return repository
                .save(UserSocialLoginJpaEntity.from(userSocialLogin))
                .toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSocialLogin> findByProviderAndProviderUserId(
            final String provider,
            final String providerUserId
    ) {
        return repository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserSocialLoginJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSocialLogin> findByUserId(final UserId userId) {
        return repository.findByUserId(userId.getValue())
                .stream()
                .map(UserSocialLoginJpaEntity::toAggregate)
                .toList();
    }

    @Override
    public void deleteByProviderAndProviderUserId(final String provider, final String providerUserId) {
        repository.deleteByProviderAndProviderUserId(provider, providerUserId);
    }
}
