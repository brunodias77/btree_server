package com.btree.infrastructure.catalog.persistence;



import com.btree.infrastructure.catalog.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id);

    @Query("SELECT c FROM CategoryJpaEntity c WHERE c.deletedAt IS NULL ORDER BY c.sortOrder ASC, c.name ASC")
    List<CategoryJpaEntity> findAllActive();

    @Query("SELECT c FROM CategoryJpaEntity c WHERE c.parentId = :parentId AND c.deletedAt IS NULL ORDER BY c.sortOrder ASC")
    List<CategoryJpaEntity> findActiveByParentId(@Param("parentId") UUID parentId);

    @Query("SELECT c FROM CategoryJpaEntity c WHERE c.parentId IS NULL AND c.deletedAt IS NULL ORDER BY c.sortOrder ASC")
    List<CategoryJpaEntity> findRoots();
}

