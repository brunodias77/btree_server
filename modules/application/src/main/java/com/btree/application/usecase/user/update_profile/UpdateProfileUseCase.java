package com.btree.application.usecase.user.update_profile;

import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class UpdateProfileUseCase implements UseCase<UpdateProfileCommand, UpdateProfileOutput> {

    private final ProfileGateway profileGateway;
    private final TransactionManager transactionManager;

    public UpdateProfileUseCase(ProfileGateway profileGateway, TransactionManager transactionManager) {
        this.profileGateway = profileGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateProfileOutput> execute(UpdateProfileCommand updateProfileCommand) {

        final var notification = Notification.create();

        // validar presenca e formato do userId
        if(updateProfileCommand.userId() == null || updateProfileCommand.userId().isBlank()){
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;

        try{
            userId = UserId.from(UUID.fromString(updateProfileCommand.userId()));
        }catch (IllegalArgumentException e){
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        // buscar perfil existente
        final var profileOpt = this.profileGateway.findByUserId(userId);
        if(profileOpt.isEmpty()){
            notification.append(ProfileError.PROFILE_NOT_FOUND);
            return Left(notification);
        }

        final var profile = profileOpt.get();

        // verificar unicidade de CPF (se fornecido)
        final var cpf = updateProfileCommand.cpf();
        if(cpf != null && cpf.isBlank()){
            if(this.profileGateway.existsByCpfAndNotUserId(cpf, userId)){
                notification.append(ProfileError.CPF_ALREADY_IN_USE);
            }
        }

        if(notification.hasError()){
            return Left(notification);
        }

        // mutar o aggregate
        profile.updatePersonalData(
                updateProfileCommand.firtName(),
                updateProfileCommand.lastName(),
                cpf != null && cpf.isBlank() ? null : cpf,
                updateProfileCommand.birthDate(),
                updateProfileCommand.gender(),
                updateProfileCommand.preferredLanguage(),
                updateProfileCommand.preferredCurrency(),
                updateProfileCommand.newsletterSubscribed()
        );

        // validar invariantes apos mutacao
        if(notification.hasError()){
            return Left(notification);
        }

        // persistir dentro da transacao
        return Try(() -> this.transactionManager.execute(() -> {
            final var updated = this.profileGateway.update(profile);
            return UpdateProfileOutput.from(updated);
        })).toEither().mapLeft(Notification::create);

    }
}
