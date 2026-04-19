package com.btree.application.usecase.user.address.update_address;

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
 * Caso de uso UC-18 — UpdateAddress [CMD P0].
 *
 * <p>Atualiza os dados de um endereço existente do usuário autenticado.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Valida presença e formato do {@code addressId}.</li>
 *   <li>Busca o endereço pelo ID.</li>
 *   <li>Verifica soft-delete.</li>
 *   <li>Verifica posse (ownership) — segurança crítica.</li>
 *   <li>Muta o aggregate com {@code updateData(...)}.</li>
 *   <li>Valida invariantes após mutação.</li>
 *   <li>Persiste dentro da transação.</li>
 *   <li>Retorna {@link UpdateAddressOutput}.</li>
 * </ol>
 */
public class UpdateAddressUseCase implements UseCase<UpdateAddressCommand, UpdateAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public UpdateAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateAddressOutput> execute(UpdateAddressCommand updateAddressCommand) {

        final var notification = Notification.create();

        //  Validar userId
        if (updateAddressCommand.userId() == null || updateAddressCommand.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(updateAddressCommand.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }


        // Validar addressId
        if (updateAddressCommand.addressId() == null || updateAddressCommand.addressId().isBlank()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final AddressId addressId;
        try {
            addressId = AddressId.from(UUID.fromString(updateAddressCommand.addressId()));
        } catch (IllegalArgumentException e) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        // Buscar endereço
        final var addressOpt = this.addressGateway.findById(addressId);
        if (addressOpt.isEmpty()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final var address = addressOpt.get();

        // Verificar soft-delete
        if (address.isDeleted()) {
            notification.append(AddressError.ADDRESS_ALREADY_DELETED);
            return Left(notification);
        }

        // Verificar posse — segurança crítica
        if (!address.getUserId().getValue().equals(userId.getValue())) {
            notification.append(AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER);
            return Left(notification);
        }

        // Mutar o aggregate
        address.updateData(
                updateAddressCommand.label(),
                updateAddressCommand.recipientName(),
                updateAddressCommand.street(),
                updateAddressCommand.number(),
                updateAddressCommand.complement(),
                updateAddressCommand.neighborhood(),
                updateAddressCommand.city(),
                updateAddressCommand.state(),
                updateAddressCommand.postalCode(),
                updateAddressCommand.country(),
                updateAddressCommand.isBillingAddress()
        );

        // Validar invariantes após mutação
        address.validate(notification);
        if (notification.hasError()) {
            return Left(notification);
        }

        // Persistir dentro da transação
        return Try(() -> this.transactionManager.execute(() -> {
            final var updated = this.addressGateway.update(address);
            return UpdateAddressOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
