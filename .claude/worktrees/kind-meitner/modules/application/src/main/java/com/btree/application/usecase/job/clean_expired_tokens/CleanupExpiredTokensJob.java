package com.btree.application.usecase.job.clean_expired_tokens;

import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.JobResult;
import com.btree.shared.usecase.JobUseCase;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import static io.vavr.API.Try;

/**
 * UC-13 — Limpeza periódica de tokens expirados (JOB).
 *
 * <p>Remove em batch os tokens de verificação de e-mail, reset de senha e
 * refresh que já ultrapassaram seu {@code expires_at}. Executado diariamente
 * pelo scheduler ({@code ScheduledJobsConfig}) para evitar acúmulo de
 * registros órfãos na tabela {@code users.user_tokens}.
 *
 * <h3>Fluxo de execução</h3>
 * <ol>
 *   <li>Recebe o {@link CleanupExpiredTokens} com o tamanho do batch.</li>
 *   <li>Abre uma transação via {@link TransactionManager}.</li>
 *   <li>Deleta até {@code batchSize} tokens expirados via {@link UserTokenGateway}.</li>
 *   <li>Retorna {@link JobResult} com a contagem de tokens removidos.</li>
 * </ol>
 *
 * <p>Em caso de falha (ex.: indisponibilidade do banco), retorna
 * {@code Left(Notification)} sem propagar a exceção — o scheduler
 * loga o erro e tenta novamente na próxima execução.
 *
 * @see CleanupExpiredTokens
 * @see JobUseCase
 * @see JobResult
 */
public class CleanupExpiredTokensJob implements JobUseCase<CleanupExpiredTokens> {

    private final UserTokenGateway userTokenGateway;
    private final TransactionManager transactionManager;

    public CleanupExpiredTokensJob(
            final UserTokenGateway userTokenGateway,
            final TransactionManager transactionManager
    ) {
        this.userTokenGateway = userTokenGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, JobResult> execute(final CleanupExpiredTokens command) {
        return Try(() -> transactionManager.execute(() -> {
            final int deleted = userTokenGateway.deleteExpired(command.batchSize());
            return JobResult.success(deleted);
        })).toEither().mapLeft(throwable ->
                Notification.create(new Error(throwable.getMessage()))
        );
    }
}
