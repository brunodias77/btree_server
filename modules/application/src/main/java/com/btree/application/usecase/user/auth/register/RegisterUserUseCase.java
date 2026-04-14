package com.btree.application.usecase.user.auth.register;

import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.domain.user.validator.UserValidator;
import com.btree.shared.contract.EmailService;
import com.btree.shared.contract.PasswordHasher;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.event.IntegrationEventPublisher;
import com.btree.shared.event.user.UserRegisteredIntegrationEvent;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class RegisterUserUseCase implements UseCase<RegisterUserCommand, RegisterUserOutput> {

    private static final long EMAIL_VERIFICATION_EXPIRATION_HOURS = 24L;
    private final UserGateway _userGateway;
    private final UserTokenGateway _userTokenGateway;
    private final PasswordHasher _passwordHasher;
    private final TokenHasher _tokenHasher;
    private final DomainEventPublisher _eventPublisher;
    private final IntegrationEventPublisher _integrationEventPublisher;
    private final TransactionManager _transactionManager;
    private final EmailService _emailService;

    public RegisterUserUseCase(UserGateway _userGateway, UserTokenGateway _userTokenGateway, PasswordHasher _passwordHasher, TokenHasher _tokenHasher, DomainEventPublisher _eventPublisher, IntegrationEventPublisher _integrationEventPublisher, TransactionManager _transactionManager, EmailService emailService) {
        this._userGateway = _userGateway;
        this._userTokenGateway = _userTokenGateway;
        this._passwordHasher = _passwordHasher;
        this._tokenHasher = _tokenHasher;
        this._eventPublisher = _eventPublisher;
        this._integrationEventPublisher = _integrationEventPublisher;
        this._transactionManager = _transactionManager;
        this._emailService = emailService;
    }


    @Override
    public Either<Notification, RegisterUserOutput> execute(RegisterUserCommand registerUserCommand) {

        final var notification = Notification.create();

        // 1. Validar senha bruta ANTES do hash (R7-R9)
        UserValidator.validatePassword(registerUserCommand.password(), notification);

        // 2. Verificar unicidade — acumula ambos os erros antes de retornar
        if(registerUserCommand.username() != null && _userGateway.existsByUsername(registerUserCommand.username().toLowerCase())){
            notification.append(UserError.USERNAME_ALREADY_EXISTS);
        }
        if(registerUserCommand.email() != null && _userGateway.existsByEmail(registerUserCommand.email().toLowerCase())){
            notification.append(UserError.EMAIL_ALREADY_EXISTS);
        }

        if(notification.hasError()){
            return Left(notification);
        }

        // 3. Normalizar e fazer hash
        final String normalizedUserName = registerUserCommand.username().toLowerCase();
        final String normalizedEmail = registerUserCommand.email().toLowerCase();
        final String passwordHash = _passwordHasher.hash(registerUserCommand.password());

        // 4. Criar aggregate — valida invariantes, cria Profile + NotificationPreference + UserCreatedEvent
        final var user = User.create(normalizedUserName, normalizedEmail, passwordHash, notification);

        if (notification.hasError()) {
            return Left(notification);
        }

        // 5. Gerar token de verificação de e-mail
        final var rawToken = _tokenHasher.generate();
        final var tokenHash = _tokenHasher.hash(rawToken);
        final var tokenExpiresAt = Instant.now().plus(EMAIL_VERIFICATION_EXPIRATION_HOURS, ChronoUnit.HOURS);
        final var verificationToken = UserToken.create(
                user.getId(),
                TokenType.EMAIL_VERIFICATION.name(),
                tokenHash,
                tokenExpiresAt
        );
        return Try(() -> _transactionManager.execute(() -> {
            final var savedUser = _userGateway.save(user);
            _userGateway.assignRole(savedUser.getId(), "customer");
            _userTokenGateway.create(verificationToken);
            _eventPublisher.publishAll(user.getDomainEvents());

            // 7. Publicar integration event — outros módulos reagem após o commit desta transação
            //    sem acoplamento direto (ex: cart cria carrinho para o novo usuário).
            //    O SpringIntegrationEventPublisher usa @TransactionalEventListener(AFTER_COMMIT)
            //    nos consumers, garantindo que a reação só ocorre se o registro for commitado.
            _integrationEventPublisher.publish(
                    new UserRegisteredIntegrationEvent(
                            savedUser.getId().getValue(),
                            savedUser.getEmail()
                    )
            );

            // 8. Enviar e-mail de verificação (fora da transação para não bloquear o commit)
            _emailService.sendEmailVerification(savedUser.getEmail(), savedUser.getUsername(), rawToken);

            return RegisterUserOutput.from(savedUser);
        })).toEither().mapLeft(Notification::create);
    }
}
