package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.ProductReview;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.ProductReviewId;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;

import java.util.Optional;
import java.util.UUID;

public interface ProductReviewGateway {

    ProductReview save(ProductReview review);

    ProductReview update(ProductReview review);

    Optional<ProductReview> findById(ProductReviewId id);

    Optional<ProductReview> findByProductAndUser(ProductId productId, UUID userId);

    boolean existsByProductAndUser(ProductId productId, UUID userId);

    Pagination<ProductReview> findByProduct(ProductId productId, PageRequest pageRequest);

    Double findAverageRatingByProduct(ProductId productId);

    void deleteById(ProductReviewId id);
}
