package com.btree.infrastructure.cart.repository;

import com.btree.infrastructure.cart.entity.CartJpaEntity;
import com.btree.shared.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartJpaRepository extends JpaRepository<CartJpaEntity, UUID> {

    Optional<CartJpaEntity> findByUserIdAndStatus(UUID userId, CartStatus status);

    Optional<CartJpaEntity> findBySessionIdAndStatus(String sessionId, CartStatus status);

    List<CartJpaEntity> findByStatus(CartStatus status);

    @Query("SELECT c FROM CartJpaEntity c WHERE c.status = 'ACTIVE' AND c.expiresAt IS NOT NULL AND c.expiresAt < :now")
    List<CartJpaEntity> findExpiredActiveCarts(@Param("now") Instant now);
}

