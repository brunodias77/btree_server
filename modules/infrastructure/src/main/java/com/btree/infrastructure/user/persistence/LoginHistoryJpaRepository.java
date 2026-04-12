package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.LoginHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface LoginHistoryJpaRepository
        extends JpaRepository<LoginHistoryJpaEntity, LoginHistoryJpaEntity.LoginHistoryPk> {
}
