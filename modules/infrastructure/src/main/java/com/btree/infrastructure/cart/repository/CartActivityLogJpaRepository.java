package com.btree.infrastructure.cart.repository;

import com.btree.infrastructure.cart.entity.CartActivityLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartActivityLogJpaRepository extends JpaRepository<CartActivityLogJpaEntity, UUID> {

    List<CartActivityLogJpaEntity> findByCartIdOrderByCreatedAtAsc(UUID cartId);
}