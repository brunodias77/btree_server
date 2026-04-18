package com.btree.shared.usecase;

/**
 * Resultado da execução de um {@link JobUseCase}.
 *
 * <p>Encapsula métricas básicas que todo job periódico deve reportar após a execução,
 * permitindo que o scheduler (ou sistema de monitoramento) registre informações
 * úteis para observabilidade e debugging.
 *
 * <h3>Exemplo de uso no scheduler</h3>
 * <pre>{@code
 * @Scheduled(cron = "0 0 3 * * *")
 * public void cleanupExpiredTokens() {
 *     cleanupExpiredTokensJob
 *         .execute(CleanupExpiredTokens.withDefaultBatch())
 *         .peek(result -> log.info("Job finalizado: {}", result))
 *         .peekLeft(notification ->
 *             log.error("Falha no job: {}", notification.getErrors()));
 * }
 * }</pre>
 *
 * <h3>Imutabilidade</h3>
 * <p>Sendo um {@code record}, esta classe é imutável e thread-safe por definição.
 *
 * @param processed quantidade de itens processados com sucesso
 * @param skipped   quantidade de itens ignorados (ex.: idempotência, já processados)
 * @param failed    quantidade de itens que falharam durante o processamento
 */
public record JobResult(int processed, int skipped, int failed) {

    /**
     * Cria um resultado com contagens de sucesso, ignorados e falha.
     *
     * @param processed total de itens processados com sucesso
     * @param skipped   total de itens ignorados
     * @param failed    total de itens que falharam
     * @return instância imutável de {@code JobResult}
     */
    public static JobResult of(final int processed, final int skipped, final int failed) {
        return new JobResult(processed, skipped, failed);
    }

    /**
     * Cria um resultado quando todos os itens foram processados sem falha nem skips.
     *
     * @param processed total de itens processados
     * @return instância com {@code skipped = 0} e {@code failed = 0}
     */
    public static JobResult success(final int processed) {
        return new JobResult(processed, 0, 0);
    }

    /**
     * Cria um resultado vazio (nenhum item processado, ignorado ou falhado).
     *
     * <p>Útil quando o job executa mas não encontra itens pendentes.
     *
     * @return instância com todos os contadores zerados
     */
    public static JobResult empty() {
        return new JobResult(0, 0, 0);
    }

    /**
     * Total de itens que o job encontrou (sucesso + ignorados + falha).
     *
     * @return soma de {@code processed}, {@code skipped} e {@code failed}
     */
    public int total() {
        return processed + skipped + failed;
    }

    /**
     * Indica se houve pelo menos uma falha durante a execução.
     *
     * @return {@code true} se {@code failed > 0}
     */
    public boolean hasFailures() {
        return failed > 0;
    }

    @Override
    public String toString() {
        if (skipped > 0) {
            return "JobResult{processed=%d, skipped=%d, failed=%d, total=%d}"
                    .formatted(processed, skipped, failed, total());
        }
        return "JobResult{processed=%d, failed=%d, total=%d}"
                .formatted(processed, failed, total());
    }
}
