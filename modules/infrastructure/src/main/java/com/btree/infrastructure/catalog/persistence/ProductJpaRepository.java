package com.btree.infrastructure.catalog.persistence;



import com.btree.infrastructure.catalog.entity.ProductJpaEntity;
import com.btree.shared.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository
        extends JpaRepository<ProductJpaEntity, UUID>,
        JpaSpecificationExecutor<ProductJpaEntity> {

    Optional<ProductJpaEntity> findBySlugAndDeletedAtIsNull(String slug);

    Optional<ProductJpaEntity> findBySkuAndDeletedAtIsNull(String sku);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    boolean existsBySkuAndIdNotAndDeletedAtIsNull(String sku, UUID id);

    Page<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductJpaEntity> findByCategoryIdAndDeletedAtIsNull(UUID categoryId, Pageable pageable);

    Page<ProductJpaEntity> findByBrandIdAndDeletedAtIsNull(UUID brandId, Pageable pageable);

    Page<ProductJpaEntity> findByStatusAndDeletedAtIsNull(ProductStatus status, Pageable pageable);

    Page<ProductJpaEntity> findByCategoryIdAndStatusAndDeletedAtIsNull(
            UUID categoryId, ProductStatus status, Pageable pageable);

    Page<ProductJpaEntity> findByBrandIdAndStatusAndDeletedAtIsNull(
            UUID brandId, ProductStatus status, Pageable pageable);

    Page<ProductJpaEntity> findByFeaturedTrueAndStatusAndDeletedAtIsNull(
            ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductJpaEntity> findAllByIdInAndDeletedAtIsNull(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
