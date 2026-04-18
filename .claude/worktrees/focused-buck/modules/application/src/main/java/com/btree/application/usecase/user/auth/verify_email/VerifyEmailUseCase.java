package com.btree.application.usecase.user.auth.verify_email;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.TokenType;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso #3 — Verificação de e-mail.
 *
 * <p>Retorna {@code Left(Notification)} quando o token é inválido, expirado,
 * já utilizado ou o e-mail já está verificado; {@code Right(Void)} em caso de sucesso.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Recebe o token em texto claro enviado ao usuário via e-mail</li>
 *   <li>Hasheia e busca o {@code UserToken} correspondente</li>
 *   <li>Valida tipo (EMAIL_VERIFICATION), expiração e uso anterior</li>
 *   <li>Verifica se o e-mail já está confirmado</li>
 *   <li>Em transação: marca o token como usado, atualiza {@code email_verified = true} no usuário
 *       e publica {@code UserEmailVerifiedEvent}</li>
 * </ol>
 */
public class VerifyEmailUseCase implements UnitUseCase<VerifyEmailCommand> {

    private final UserTokenGateway _userTokenGateway;
    private final UserGateway _userGateway;
    private final TokenHasher _tokenHasher;
    private final TransactionManager _transactionManager;

    public VerifyEmailUseCase(UserTokenGateway _userTokenGateway, UserGateway _userGateway, TokenHasher _tokenHasher, TransactionManager _transactionManager) {
        this._userTokenGateway = _userTokenGateway;
        this._userGateway = _userGateway;
        this._tokenHasher = _tokenHasher;
        this._transactionManager = _transactionManager;
    }


    @Override
    public Either<Notification, Void> execute(VerifyEmailCommand verifyEmailCommand) {

        final var notification = Notification.create();

        // 1. Hashear e buscar token
        final var tokenHash = _tokenHasher.hash(verifyEmailCommand.token());
        final var tokenOpt = _userTokenGateway.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            notification.append(UserError.TOKEN_NOT_FOUND);
            return Left(notification);
        }

        final var userToken = tokenOpt.get();

        // 2. Validar tipo
        if (!TokenType.EMAIL_VERIFICATION.name().equals(userToken.getTokenType())) {
            notification.append(UserError.TOKEN_INVALID_TYPE);
            return Left(notification);
        }

        // 3. Validar expiração e uso
        if (userToken.isExpired()) {
            notification.append(UserError.TOKEN_EXPIRED);
            return Left(notification);
        }
        if (userToken.isUsed()) {
            notification.append(UserError.TOKEN_ALREADY_USED);
            return Left(notification);
        }

        // 4. Buscar usuário
        final var userOpt = _userGateway.findById(userToken.getUserId());
        if (userOpt.isEmpty()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final var user = userOpt.get();

        // 5. Verificar se e-mail já está confirmado
        if (user.isEmailVerified()) {
            notification.append(UserError.EMAIL_ALREADY_VERIFIED);
            return Left(notification);
        }

        // 6. Persistir: marcar token como usado + verificar e-mail do usuário
        return Try(() -> _transactionManager.execute(() -> {
            userToken.markAsUsed();
            _userTokenGateway.update(userToken);

            user.verifyEmail();
            _userGateway.update(user);

            return (Void) null;
        })).toEither().mapLeft(Notification::create);    }
}
