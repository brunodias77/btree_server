package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.AddressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, UUID> {

    @Query("SELECT a FROM AddressJpaEntity a WHERE a.user.id = :userId AND a.deletedAt IS NULL ORDER BY a.isDefault DESC, a.createdAt ASC")
    List<AddressJpaEntity> findAllActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(a) FROM AddressJpaEntity a WHERE a.user.id = :userId AND a.deletedAt IS NULL")
    long countActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(a) FROM AddressJpaEntity a
            WHERE a.user.id = :userId
              AND a.id <> :excludeId
              AND a.deletedAt IS NULL
            """)
    long countActiveByUserIdExcluding(
            @Param("userId") UUID userId,
            @Param("excludeId") UUID excludeId
    );

    @Modifying
    @Query("""
            UPDATE AddressJpaEntity a
            SET a.isDefault = false, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.user.id = :userId
              AND a.isDefault = true
              AND a.deletedAt IS NULL
            """)
    void clearDefaultByUserId(@Param("userId") UUID userId);
}
