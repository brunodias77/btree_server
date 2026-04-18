package com.btree.infrastructure.coupon.persistence;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.gateway.CouponGateway;
import com.btree.domain.coupon.identifier.CouponId;
import com.btree.infrastructure.coupon.entity.CouponJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementação de {@link CouponGateway} com Spring Data JPA / PostgreSQL.
 *
 * <p>Convenções:
 * <ul>
 *   <li>{@code findById} usa {@code findByIdAndDeletedAtIsNull} para respeitar o soft-delete.</li>
 *   <li>{@code update} carrega a entidade gerenciada, chama {@code updateFrom} e deixa o Hibernate
 *       detectar as mudanças via dirty-checking — inclui DELETE + INSERT nas coleções de elegibilidade.</li>
 *   <li>O campo {@code version} em {@link CouponJpaEntity} usa {@code @Version} para optimistic locking;
 *       conflitos disparam {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 * </ul>
 */
@Component
@Transactional
public class CouponPostgresGateway implements CouponGateway {

    private final CouponJpaRepository couponJpaRepository;

    public CouponPostgresGateway(final CouponJpaRepository couponJpaRepository) {
        this.couponJpaRepository = couponJpaRepository;
    }

    // ── Escritas ──────────────────────────────────────────────

    @Override
    public Coupon save(final Coupon coupon) {
        return couponJpaRepository
                .save(CouponJpaEntity.from(coupon))
                .toAggregate();
    }

    @Override
    public Coupon update(final Coupon coupon) {
        final var entity = couponJpaRepository.findById(coupon.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Coupon.class, coupon.getId().getValue()));
        entity.updateFrom(coupon);
        return couponJpaRepository.save(entity).toAggregate();
    }

    // ── Leituras ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<Coupon> findById(final CouponId id) {
        return couponJpaRepository
                .findByIdAndDeletedAtIsNull(id.getValue())
                .map(CouponJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(final String code) {
        return couponJpaRepository.existsByCode(code);
    }
}
