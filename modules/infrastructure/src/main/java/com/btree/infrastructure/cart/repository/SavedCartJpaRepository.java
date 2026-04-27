package com.btree.infrastructure.cart.repository;

import com.btree.infrastructure.cart.entity.SavedCartJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedCartJpaRepository extends JpaRepository<SavedCartJpaEntity, UUID> {

    List<SavedCartJpaEntity> findByUserId(UUID userId);
}
