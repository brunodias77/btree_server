package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByName(String name);

    boolean existsByName(String name);
}
