package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.SessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Query(value = """
        UPDATE users.sessions
        SET revoked = true,
            version = version + 1
        WHERE refresh_token_hash = :refreshTokenHash
          AND revoked = false
          AND expires_at > :now
        RETURNING *
        """, nativeQuery = true)
    Optional<SessionJpaEntity> revokeActiveByRefreshTokenHash(
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
        UPDATE SessionJpaEntity s
        SET s.revoked = true
        WHERE s.userId = :userId
          AND s.revoked = false
          AND s.expiresAt > :now
        """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
