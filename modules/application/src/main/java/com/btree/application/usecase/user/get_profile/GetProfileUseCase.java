package com.btree.application.usecase.user.get_profile;

import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

public class GetProfileUseCase implements UseCase<GetProfileCommand, GetProfileOutput> {

    private final ProfileGateway profileGateway;

    public GetProfileUseCase(ProfileGateway profileGateway) {
        this.profileGateway = profileGateway;
    }

    @Override
    public Either<Notification, GetProfileOutput> execute(GetProfileCommand getProfileCommand) {
        final var notification = Notification.create();

        if (getProfileCommand.userId() == null || getProfileCommand.userId().isBlank()) {
            notification.append(ProfileError.PROFILE_NOT_FOUND);
            return Left(notification);
        }

        final var userId = UserId.from(getProfileCommand.userId());
        final var profileOpt = this.profileGateway.findByUserId(userId);

        if (profileOpt.isEmpty()) {
            notification.append(ProfileError.PROFILE_NOT_FOUND);
            return Left(notification);
        }

        return Right(GetProfileOutput.from(profileOpt.get()));
    }
}
