package com.btree.application.usecase.coupon.update;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.error.CouponError;
import com.btree.domain.coupon.gateway.CouponGateway;
import com.btree.domain.coupon.identifier.CouponId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.Set;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class UpdateCouponUseCase implements UseCase<UpdateCouponCommand, UpdateCouponOutput> {

    private static final Set<CouponStatus> EDITABLE_STATUSES = Set.of(
            CouponStatus.DRAFT,
            CouponStatus.ACTIVE,
            CouponStatus.INACTIVE,
            CouponStatus.PAUSED
    );

    private final CouponGateway couponGateway;
    private final TransactionManager transactionManager;

    public UpdateCouponUseCase(
            final CouponGateway couponGateway,
            final TransactionManager transactionManager
    ) {
        this.couponGateway      = couponGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateCouponOutput> execute(final UpdateCouponCommand command) {
        final var notification = Notification.create();

        // 1. Buscar o aggregate — lança NotFoundException (404) se não existir
        final var coupon = couponGateway.findById(CouponId.from(command.id()))
                .orElseThrow(() -> NotFoundException.with(Coupon.class, command.id()));

        // 2a. Verificar soft-delete
        if (coupon.isDeleted()) {
            notification.append(CouponError.COUPON_DELETED);
            return Left(notification);
        }

        // 2b. Verificar status editável
        if (!EDITABLE_STATUSES.contains(coupon.getStatus())) {
            notification.append(new Error(
                    String.format(CouponError.COUPON_NOT_EDITABLE.message(), coupon.getStatus().name())
            ));
            return Left(notification);
        }

        // 2c. max_uses não pode ser reduzido abaixo de current_uses
        if (command.maxUses() != null && command.maxUses() < coupon.getCurrentUses()) {
            notification.append(new Error(
                    String.format(CouponError.MAX_USES_BELOW_CURRENT.message(), coupon.getCurrentUses())
            ));
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Aplicar mutação — validações estruturais acumuladas no mesmo notification
        coupon.update(
                command.description(),
                command.discountValue(),
                command.minOrderValue(),
                command.maxDiscountAmount(),
                command.maxUses(),
                command.maxUsesPerUser(),
                command.startsAt(),
                command.expiresAt(),
                command.eligibleCategoryIds(),
                command.eligibleProductIds(),
                command.eligibleBrandIds(),
                command.eligibleUserIds(),
                notification
        );

        if (notification.hasError()) {
            return Left(notification);
        }

        // 4. Persistir dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var updated = couponGateway.update(coupon);
            return UpdateCouponOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
