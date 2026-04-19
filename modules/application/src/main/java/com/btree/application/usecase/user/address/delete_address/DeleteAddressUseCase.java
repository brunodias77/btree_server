package com.btree.application.usecase.user.address.delete_address;

import com.btree.domain.user.error.AddressError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-19 — DeleteAddress [CMD P0].
 *
 * <p>Aplica soft delete em um endereço do usuário autenticado,
 * preenchendo {@code deleted_at} sem remover o registro fisicamente.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O endereço deve existir e não estar já deletado.</li>
 *   <li>O endereço deve pertencer ao {@code userId} do JWT.</li>
 *   <li>Se o endereço é o padrão ({@code isDefault = true}) e existem
 *       outros endereços ativos, a operação é bloqueada — o usuário deve
 *       primeiro designar outro como padrão via UC-20.</li>
 *   <li>Se o endereço é o padrão e é o único endereço ativo, a deleção
 *       é permitida (usuário ficará sem endereço padrão).</li>
 * </ul>
 */
public class DeleteAddressUseCase implements UnitUseCase<DeleteAddressCommand> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public DeleteAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(DeleteAddressCommand deleteAddressCommand) {
        final var notification = Notification.create();

        if (deleteAddressCommand.userId() == null || deleteAddressCommand.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(deleteAddressCommand.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        if (deleteAddressCommand.addressId() == null || deleteAddressCommand.addressId().isBlank()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final AddressId addressId;
        try {
            addressId = AddressId.from(UUID.fromString(deleteAddressCommand.addressId()));
        } catch (IllegalArgumentException e) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final var addressOpt = this.addressGateway.findById(addressId);
        if (addressOpt.isEmpty()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final var address = addressOpt.get();

        if (address.isDeleted()) {
            notification.append(AddressError.ADDRESS_ALREADY_DELETED);
            return Left(notification);
        }

        if (!address.getUserId().getValue().equals(userId.getValue())) {
            notification.append(AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER);
            return Left(notification);
        }


        if (address.isDefault()) {
            final long otherActiveCount =
                    this.addressGateway.countActiveByUserIdExcluding(userId, addressId);
            if (otherActiveCount > 0) {
                notification.append(AddressError.CANNOT_DELETE_DEFAULT_ADDRESS);
                return Left(notification);
            }
        }


        return Try(() -> transactionManager.execute(() -> {
            address.softDelete();
            addressGateway.update(address);
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
