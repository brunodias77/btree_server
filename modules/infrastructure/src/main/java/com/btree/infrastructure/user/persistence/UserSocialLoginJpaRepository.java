package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.UserSocialLoginJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSocialLoginJpaRepository extends JpaRepository<UserSocialLoginJpaEntity, UUID> {

    Optional<UserSocialLoginJpaEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<UserSocialLoginJpaEntity> findByUserId(UUID userId);

    void deleteByProviderAndProviderUserId(String provider, String providerUserId);
}
