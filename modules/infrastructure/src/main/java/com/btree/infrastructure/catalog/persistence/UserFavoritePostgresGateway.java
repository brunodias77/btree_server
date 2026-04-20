package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.UserFavorite;
import com.btree.domain.catalog.gateway.UserFavoriteGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.infrastructure.catalog.entity.UserFavoriteJpaEntity;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional
public class UserFavoritePostgresGateway implements UserFavoriteGateway {

    private final UserFavoriteJpaRepository userFavoriteJpaRepository;

    public UserFavoritePostgresGateway(final UserFavoriteJpaRepository userFavoriteJpaRepository) {
        this.userFavoriteJpaRepository = userFavoriteJpaRepository;
    }

    @Override
    public UserFavorite save(final UserFavorite favorite) {
        return userFavoriteJpaRepository
                .save(UserFavoriteJpaEntity.from(favorite))
                .toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserAndProduct(final UUID userId, final ProductId productId) {
        return userFavoriteJpaRepository
                .existsByIdUserIdAndIdProductId(userId, productId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<UserFavorite> findByUser(final UUID userId, final PageRequest pageRequest) {
        final var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), Sort.by(Sort.Direction.DESC, "createdAt")
        );
        final var page = userFavoriteJpaRepository.findByIdUserId(userId, pageable);
        return Pagination.of(
                page.getContent().stream().map(UserFavoriteJpaEntity::toAggregate).toList(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements()
        );
    }

    @Override
    public void deleteByUserAndProduct(final UUID userId, final ProductId productId) {
        userFavoriteJpaRepository.deleteByIdUserIdAndIdProductId(userId, productId.getValue());
    }
}
