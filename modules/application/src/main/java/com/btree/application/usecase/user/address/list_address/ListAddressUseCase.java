package com.btree.application.usecase.user.address.list_address;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

public class ListAddressUseCase implements QueryUseCase<ListAddressCommand, ListAddressOutput> {

    private final AddressGateway addressGateway;

    public ListAddressUseCase(final AddressGateway addressGateway) {
        this.addressGateway = addressGateway;
    }

    @Override
    public Either<Notification, ListAddressOutput> execute(final ListAddressCommand command) {
        final var notification = Notification.create();

        if (command.userId() == null || command.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(command.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var addresses = this.addressGateway.findByUserId(userId);
        return Right(ListAddressOutput.from(addresses));
    }
}
