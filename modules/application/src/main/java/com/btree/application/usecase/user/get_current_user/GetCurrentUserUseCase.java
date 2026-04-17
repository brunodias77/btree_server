package com.btree.application.usecase.user.get_current_user;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

public class GetCurrentUserUseCase implements QueryUseCase<GetCurrentUserInput, GetCurrentUserOutput> {

    private final UserGateway _userGateway;

    public GetCurrentUserUseCase(UserGateway userGateway) {
        this._userGateway = userGateway;
    }


    @Override
    public Either<Notification, GetCurrentUserOutput> execute(GetCurrentUserInput getCurrentUserInput) {

        final var notification = Notification.create();

        if(getCurrentUserInput.userId() == null || getCurrentUserInput.userId().isBlank()){
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var userId = UserId.from(UUID.fromString(getCurrentUserInput.userId()));
        final var userOpt = _userGateway.findById(userId);

        if(userOpt.isEmpty()){
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        return Right(GetCurrentUserOutput.from(userOpt.get()));

    }
}
