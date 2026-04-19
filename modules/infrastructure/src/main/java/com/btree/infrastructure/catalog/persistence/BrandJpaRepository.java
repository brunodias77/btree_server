package com.btree.infrastructure.catalog.persistence;


import com.btree.infrastructure.catalog.entity.BrandJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, UUID> {

    Optional<BrandJpaEntity> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id);

    @Query("SELECT b FROM BrandJpaEntity b WHERE b.deletedAt IS NULL ORDER BY b.name ASC")
    List<BrandJpaEntity> findAllActive();
}
