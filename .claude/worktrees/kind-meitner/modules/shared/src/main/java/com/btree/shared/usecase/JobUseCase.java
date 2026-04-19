package com.btree.shared.usecase;

import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

/**
 * Contrato base para casos de uso do tipo <b>JOB</b> (tarefas agendadas/periódicas).
 *
 * <p>Jobs são operações executadas em background pelo scheduler (ex.: {@code @Scheduled}
 * na camada de API). Diferem dos Use Cases convencionais pois:
 * <ul>
 *   <li>Não são disparados por requisição HTTP de um usuário.</li>
 *   <li>Podem receber um <b>Command</b> de configuração (batch size, filtros, etc.)
 *       ou {@link Void} quando não há parâmetros.</li>
 *   <li>Retornam {@link Either}: {@code Left(Notification)} se falhar;
 *       {@code Right(JobResult)} com métricas da execução se suceder.</li>
 * </ul>
 *
 * <h3>Ciclo de vida típico</h3>
 * <ol>
 *   <li>O scheduler (em {@code ScheduledJobsConfig}) invoca {@link #execute(Object)}.</li>
 *   <li>O job consulta gateways para obter itens pendentes.</li>
 *   <li>Processa cada item, acumulando contadores no {@link JobResult}.</li>
 *   <li>Retorna o resultado para o scheduler logar/auditar.</li>
 * </ol>
 *
 * <h3>Exemplo de uso</h3>
 * <pre>{@code
 * public class CleanupExpiredTokensUseCase
 *         extends JobUseCase<CleanupExpiredTokensCommand> {
 *
 *     @Override
 *     public Either<Notification, JobResult> execute(CleanupExpiredTokensCommand cmd) {
 *         int deleted = tokenGateway.deleteExpiredBefore(Instant.now(), cmd.batchSize());
 *         return Either.right(JobResult.of(deleted, 0));
 *     }
 * }
 * }</pre>
 *
 * @param <IN> Tipo do Command de configuração do job (ou {@link Void} se não houver)
 *
 * @see UseCase
 * @see UnitUseCase
 * @see JobResult
 */
public interface JobUseCase<IN> {

    /**
     * Executa o job com os parâmetros fornecidos.
     *
     * @param input comando com parâmetros de execução (batch size, filtros, etc.)
     * @return {@code Right(JobResult)} em caso de sucesso com métricas da execução;
     *         {@code Left(Notification)} em caso de falha com os erros acumulados
     */
    Either<Notification, JobResult> execute(IN input);
}
