package com.btree.infrastructure.user.persistence;


import com.btree.infrastructure.user.entity.UserTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenJpaRepository extends JpaRepository<UserTokenJpaEntity, UUID> {

    Optional<UserTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Remove fisicamente tokens expirados em lote.
     * A cláusula {@code WHERE id IN (SELECT id ... LIMIT :batchSize)} garante
     * que o DELETE respeite o limite de lote sem travar a tabela inteira.
     */
    @Modifying
    @Query(value = """
        DELETE FROM users.user_tokens
        WHERE id IN (
            SELECT id FROM users.user_tokens
            WHERE expires_at < :now
            LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}

