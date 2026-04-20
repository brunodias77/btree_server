package com.btree.infrastructure.catalog.persistence;

import com.btree.infrastructure.catalog.entity.ProductReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductReviewJpaRepository extends JpaRepository<ProductReviewJpaEntity, UUID> {

    @Query("""
            SELECT r FROM ProductReviewJpaEntity r
            WHERE r.productId = :productId
              AND r.userId = :userId
              AND r.deletedAt IS NULL
            """)
    Optional<ProductReviewJpaEntity> findByProductIdAndUserId(
            @Param("productId") UUID productId,
            @Param("userId") UUID userId
    );

    boolean existsByProductIdAndUserIdAndDeletedAtIsNull(UUID productId, UUID userId);

    Page<ProductReviewJpaEntity> findByProductIdAndDeletedAtIsNull(UUID productId, Pageable pageable);

    @Query("""
            SELECT AVG(r.rating)
            FROM ProductReviewJpaEntity r
            WHERE r.productId = :productId
              AND r.deletedAt IS NULL
            """)
    Double findAverageRatingByProductId(@Param("productId") UUID productId);
}

