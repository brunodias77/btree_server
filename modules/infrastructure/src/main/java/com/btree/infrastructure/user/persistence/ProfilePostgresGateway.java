package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.ProfileJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional
public class ProfilePostgresGateway implements ProfileGateway {

    private final ProfileJpaRepository profileJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public ProfilePostgresGateway(
            final ProfileJpaRepository profileJpaRepository,
            final UserJpaRepository userJpaRepository
    ) {
        this.profileJpaRepository = profileJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Profile create(final Profile profile) {
        final var userEntity = userJpaRepository.findById(profile.getUserId().getValue())
                .orElseThrow(() -> NotFoundException.with("User not found: " + profile.getUserId().getValue()));

        return profileJpaRepository
                .save(ProfileJpaEntity.from(profile, userEntity))
                .toAggregate();
    }

    @Override
    public Profile update(final Profile profile) {
        final var entity = profileJpaRepository.findById(profile.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Profile.class, profile.getId().getValue()));

        entity.updateFrom(profile);
        return profileJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Profile> findByUserId(final UserId userId) {
        return profileJpaRepository
                .findActiveByUserId(userId.getValue())
                .map(ProfileJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCpfAndNotUserId(final String cpf, final UserId userId) {
        return profileJpaRepository.existsByCpfAndUserIdNot(cpf, userId.getValue());
    }
}
