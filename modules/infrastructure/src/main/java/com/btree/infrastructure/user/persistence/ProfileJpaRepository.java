package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.ProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProfileJpaRepository extends JpaRepository<ProfileJpaEntity, UUID> {

    @Query("SELECT p FROM ProfileJpaEntity p WHERE p.user.id = :userId")
    Optional<ProfileJpaEntity> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT p FROM ProfileJpaEntity p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
    Optional<ProfileJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(p) > 0
            FROM ProfileJpaEntity p
            WHERE p.cpf = :cpf
              AND p.user.id <> :userId
              AND p.deletedAt IS NULL
            """)
    boolean existsByCpfAndUserIdNot(
            @Param("cpf") String cpf,
            @Param("userId") UUID userId
    );
}
