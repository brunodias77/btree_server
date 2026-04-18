package com.btree.infrastructure.coupon.persistence;

import com.btree.infrastructure.coupon.entity.CouponJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, UUID> {

    boolean existsByCode(String code);

    Optional<CouponJpaEntity> findByIdAndDeletedAtIsNull(UUID id);
}
