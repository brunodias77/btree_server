package com.btree.infrastructure.catalog.persistence;

import com.btree.infrastructure.catalog.entity.UserFavoriteJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserFavoriteJpaRepository extends JpaRepository<UserFavoriteJpaEntity, UserFavoriteJpaEntity.UserFavoritePk> {

    boolean existsByIdUserIdAndIdProductId(UUID userId, UUID productId);

    Page<UserFavoriteJpaEntity> findByIdUserId(UUID userId, Pageable pageable);

    void deleteByIdUserIdAndIdProductId(UUID userId, UUID productId);
}
