package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.UserFavorite;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;

import java.util.UUID;

public interface UserFavoriteGateway {

    UserFavorite save(UserFavorite favorite);

    boolean existsByUserAndProduct(UUID userId, ProductId productId);

    Pagination<UserFavorite> findByUser(UUID userId, PageRequest pageRequest);

    void deleteByUserAndProduct(UUID userId, ProductId productId);
}
