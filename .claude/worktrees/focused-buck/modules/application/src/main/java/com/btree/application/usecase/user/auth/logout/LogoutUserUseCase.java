package com.btree.application.usecase.user.auth.logout;

import com.btree.domain.user.error.AuthError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.*;

public class LogoutUserUseCase implements UnitUseCase<LogoutUserCommand> {

    private final SessionGateway _sessionGateway;
    private final TokenHasher _tokenHasher;
    private final TransactionManager _transactionManager;

    public LogoutUserUseCase(SessionGateway sessionGateway, TokenHasher tokenHasher, TransactionManager transactionManager) {
        this._sessionGateway = sessionGateway;
        this._tokenHasher = tokenHasher;
        this._transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(LogoutUserCommand logoutUserCommand) {

        final var notification = Notification.create();

        // validar presenca do token
        if(logoutUserCommand.refreshToken() == null || logoutUserCommand.refreshToken().isBlank()){
            notification.append(AuthError.INVALID_REFRESH_TOKEN);
            return Left(notification);
        }

        // buscar sessao pelo hash
        final var tokenHash = _tokenHasher.hash(logoutUserCommand.refreshToken());
        final var sessionOpt = _sessionGateway.findByRefreshTokenHash(tokenHash);

        // sessao inexistente - idempotencia: objetivo ja alcancado
        if(sessionOpt.isEmpty()){
            return Right(null);
        }

        final var session = sessionOpt.get();

        // se ainda estiver ativa, revogar e persistir
        if(session.isActive()){
            return Try(() -> _transactionManager.execute(() -> {
                session.revoke();
                _sessionGateway.update(session);
                return (Void) null;
            })).toEither().mapLeft(Notification::create);
        }

        // Sessão já revogada ou expirada — retorna sucesso silenciosamente
        return Right(null);
    }
}
