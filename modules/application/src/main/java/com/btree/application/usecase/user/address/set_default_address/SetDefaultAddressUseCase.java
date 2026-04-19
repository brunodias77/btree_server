package com.btree.application.usecase.user.address.set_default_address;

import com.btree.domain.user.error.AddressError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-20 — SetDefaultAddress [CMD P0].
 *
 * <p>Marca um endereço como padrão de entrega, removendo a marcação
 * de qualquer outro endereço padrão do mesmo usuário em uma transação
 * atômica.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O endereço deve existir e não estar soft-deletado.</li>
 *   <li>O endereço deve pertencer ao {@code userId} do JWT.</li>
 *   <li>Se o endereço já é o padrão, a operação é idempotente —
 *       retorna sucesso sem modificações.</li>
 * </ul>
 */
public class SetDefaultAddressUseCase implements UseCase<SetDefaultAddressCommand, SetDefaultAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public SetDefaultAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }


    @Override
    public Either<Notification, SetDefaultAddressOutput> execute(SetDefaultAddressCommand setDefaultAddressCommand) {

        final var notification = Notification.create();

        if (setDefaultAddressCommand.userId() == null || setDefaultAddressCommand.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(setDefaultAddressCommand.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        if (setDefaultAddressCommand.addressId() == null || setDefaultAddressCommand.addressId().isBlank()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final AddressId addressId;
        try {
            addressId = AddressId.from(UUID.fromString(setDefaultAddressCommand.addressId()));
        } catch (IllegalArgumentException e) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final var addressOpt = addressGateway.findById(addressId);
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

        // Idempotência — já é o padrão, retornar sucesso sem modificações
        if (address.isDefault()) {
            return Either.right(SetDefaultAddressOutput.from(address));
        }

        // Troca atômica do padrão dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            addressGateway.clearDefaultByUserId(userId);
            address.setAsDefault();
            final var updated = addressGateway.update(address);
            return SetDefaultAddressOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}

