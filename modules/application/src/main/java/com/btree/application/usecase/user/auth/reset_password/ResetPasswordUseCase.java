package com.btree.application.usecase.user.auth.reset_password;

import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.EmailService;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Duration;
import java.time.Instant;

import static io.vavr.API.Left;
import static io.vavr.API.Right;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-07 — RequestPasswordReset.
 *
 * <p>Primeiro passo do fluxo "Esqueci minha senha": gera um token temporário
 * do tipo {@code PASSWORD_RESET}, persiste-o e dispara o envio do e-mail com
 * o link de redefinição.
 *
 * <p><b>Anti-enumeration:</b> o endpoint sempre retorna sucesso ({@code Right(null)}),
 * independentemente de o e-mail existir ou não na base, evitando que atacantes
 * descubram quais e-mails estão cadastrados.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença/formato básico do e-mail</li>
 *   <li>Busca o usuário pelo e-mail — se não encontrado ou inativo, retorna sucesso silenciosamente</li>
 *   <li>Gera token opaco e seu hash</li>
 *   <li>Cria {@link UserToken} do tipo {@code PASSWORD_RESET} com expiração de 30 minutos</li>
 *   <li>Aciona {@code user.requestPasswordReset()} para registrar o domain event</li>
 *   <li>Em transação: persiste o token</li>
 *   <li>Publica domain events e envia e-mail</li>
 * </ol>
 */
public class ResetPasswordUseCase implements UnitUseCase<ResetPasswordCommand> {

    private static final Duration TOKEN_EXPIRATION = Duration.ofMinutes(30);

    private final UserGateway userGateway;
    private final UserTokenGateway userTokenGateway;
    private final TokenHasher tokenHasher;
    private final EmailService emailService;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public ResetPasswordUseCase(UserGateway userGateway, UserTokenGateway userTokenGateway, TokenHasher tokenHasher, EmailService emailService, DomainEventPublisher eventPublisher, TransactionManager transactionManager) {
        this.userGateway = userGateway;
        this.userTokenGateway = userTokenGateway;
        this.tokenHasher = tokenHasher;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }


    @Override
    public Either<Notification, Void> execute(ResetPasswordCommand resetPasswordCommand) {

        final var notification = Notification.create();

        // validar presenca do email
        if(resetPasswordCommand.email() == null || resetPasswordCommand.email().isBlank()){
            notification.append(UserError.EMAIL_EMPTY);
            return Left(notification);
        }

        // buscar usuario - antienumeration: retorna sucesso se nao encontrado/inativo
        final var userOpt = this.userGateway.findByEmail(resetPasswordCommand.email().toLowerCase());
        if(userOpt.isEmpty() || !userOpt.get().isEnabled()){
            return Right(null);
        }

        final var user = userOpt.get();

        // gerar token e hash
        final var rawToken = this.tokenHasher.generate();
        final var tokenHash = this.tokenHasher.hash(rawToken);
        final var expiresAt = Instant.now().plus(TOKEN_EXPIRATION);

        // criar entidade UserToken
        final var userToken = UserToken.create(user.getId(), TokenType.PASSWORD_RESET.name(), tokenHash, expiresAt);

        // registrar domain event no agregado
        user.requestPasswordReset(rawToken, expiresAt);

        // persistir token e publicar eventos
        return Try(() -> this.transactionManager.execute(() -> {
            this.userTokenGateway.create(userToken);
            this.eventPublisher.publishAll(user.getDomainEvents());
            this.emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), rawToken);
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
