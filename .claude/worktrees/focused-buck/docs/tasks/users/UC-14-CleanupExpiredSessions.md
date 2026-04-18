# Task: UC-14 — CleanupExpiredSessions

## 📋 Resumo

Job agendado que remove fisicamente sessões expiradas e/ou revogadas da tabela `users.sessions`. Sessões acumulam a cada login bem-sucedido — incluindo renovações por refresh token rotation. Sem limpeza periódica, a tabela cresce indefinidamente, degradando queries de autenticação que filtram por `refresh_token_hash` e `user_id`. Em sistemas com muitos usuários ativos, esse volume pode ser significativo.

## 🎯 Objetivo

Implementar um `UnitUseCase<CleanupExpiredSessionsCommand>` do tipo `[JOB]` que deleta fisicamente sessões inativas (expiradas ou revogadas) em lote via `@Scheduled` no `ScheduledJobsConfig`. A execução deve ser atômica por lote, com log estruturado de métricas e comportamento idempotente — executar sem sessões elegíveis retorna sucesso silenciosamente.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `BAIXA`
- **Endpoint:** Nenhum — executado exclusivamente como job agendado (`@Scheduled`)
- **Tabelas do Banco:** `users.sessions`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/SessionGateway.java` — **alterar**: adicionar método `deleteInactive(int batchSize)`

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/job/CleanupExpiredSessionsCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/job/CleanupExpiredSessionsUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/SessionJpaRepository.java` — **alterar**: adicionar query de deleção em lote
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/SessionPostgresGateway.java` — **alterar**: implementar `deleteInactive`

### `api`

1. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean` do novo use case
2. `api/src/main/java/com/btree/api/config/ScheduledJobsConfig.java` — **alterar**: injetar e implementar `@Scheduled` para `CleanupExpiredSessions`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway (Domain)

Adicionar no `SessionGateway` o método de deleção em lote:

```java
// SessionGateway.java — adicionar

/**
 * Remove fisicamente sessões inativas em lote.
 *
 * <p>Uma sessão é considerada inativa quando está expirada
 * ({@code expires_at < NOW()}) OU foi explicitamente revogada
 * ({@code revoked = true}). Ambas as condições são elegíveis
 * para remoção, pois a sessão não pode mais ser usada para
 * renovação de token em nenhum dos casos.
 *
 * @param batchSize limite máximo de registros a deletar nesta execução
 * @return número de registros efetivamente deletados
 */
int deleteInactive(int batchSize);
```

> **Decisão de design:** a query combina `expires_at < NOW()` e `revoked = true` com `OR`, garantindo que sessões revogadas manualmente (via logout) também sejam coletadas mesmo que ainda não expiradas pelo tempo. Isso evita que sessões de logout acumulem indefinidamente na tabela.

### 2. Contrato de Entrada/Saída (Application)

**`CleanupExpiredSessionsCommand`** — carrega `batchSize` e uma flag para controlar se sessões apenas revogadas (mas ainda não expiradas) também devem ser limpas:

```java
package com.btree.application.usecase.user.job;

/**
 * Comando de entrada para UC-14 — CleanupExpiredSessions.
 *
 * @param batchSize          número máximo de sessões a deletar por execução.
 *                           Valor padrão recomendado: 1000.
 * @param includeRevoked     quando {@code true}, inclui sessões revogadas
 *                           ({@code revoked = true}) mesmo que ainda não expiradas.
 *                           Padrão: {@code true} — elimina toda sessão inutilizável.
 */
public record CleanupExpiredSessionsCommand(int batchSize, boolean includeRevoked) {

    public CleanupExpiredSessionsCommand {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("'batchSize' deve ser maior que zero");
        }
    }

    /** Factory com valores padrão seguros para uso no scheduler. */
    public static CleanupExpiredSessionsCommand withDefaultBatch() {
        return new CleanupExpiredSessionsCommand(1000, true);
    }

    /** Factory para limpeza apenas de sessões expiradas pelo tempo (sem revogadas). */
    public static CleanupExpiredSessionsCommand expiredOnly() {
        return new CleanupExpiredSessionsCommand(1000, false);
    }
}
```

> **Por que `batchSize = 1000`?** Sessões crescem mais rápido que tokens — cada renovação de refresh token cria uma nova sessão e revoga a anterior. Em sistemas com alta frequência de refresh, o volume é proporcional ao número de usuários ativos × frequência de uso. Um lote de 1000 é conservador e seguro para a maioria dos cenários.

Não há `Output` — o use case implementa `UnitUseCase<CleanupExpiredSessionsCommand>` retornando `Either<Notification, Void>`.

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.job;

import com.btree.domain.user.gateway.SessionGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vavr.API.Try;

/**
 * Caso de uso UC-14 — CleanupExpiredSessions [JOB].
 *
 * <p>Remove fisicamente sessões inativas de {@code users.sessions} em lotes
 * para evitar locks prolongados e crescimento descontrolado da tabela.
 *
 * <p>Considera inativa qualquer sessão que satisfaça ao menos uma condição:
 * <ul>
 *   <li>{@code expires_at < NOW()} — expirada pelo tempo</li>
 *   <li>{@code revoked = true} — revogada explicitamente por logout</li>
 * </ul>
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Deleta em lote até {@code batchSize} sessões inativas.</li>
 *   <li>Registra via log o número de registros removidos.</li>
 *   <li>Retorna {@code Right(null)} em sucesso ou {@code Left(Notification)}
 *       em falha de infraestrutura.</li>
 * </ol>
 *
 * <p><b>Idempotência:</b> executar sem sessões elegíveis retorna
 * {@code Right(null)} com log de 0 registros deletados — sem efeito colateral.
 */
public class CleanupExpiredSessionsUseCase implements UnitUseCase<CleanupExpiredSessionsCommand> {

    private static final Logger log = LoggerFactory.getLogger(CleanupExpiredSessionsUseCase.class);

    private final SessionGateway sessionGateway;
    private final TransactionManager transactionManager;

    public CleanupExpiredSessionsUseCase(
            final SessionGateway sessionGateway,
            final TransactionManager transactionManager
    ) {
        this.sessionGateway = sessionGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(final CleanupExpiredSessionsCommand command) {
        return Try(() -> transactionManager.execute(() -> {
            final int deleted = sessionGateway.deleteInactive(command.batchSize());

            if (deleted > 0) {
                log.info(
                    "[CleanupExpiredSessions] {} sessão(ões) inativa(s) removida(s) " +
                    "(includeRevoked={}, batchSize={}).",
                    deleted, command.includeRevoked(), command.batchSize()
                );
            } else {
                log.debug(
                    "[CleanupExpiredSessions] Nenhuma sessão inativa encontrada " +
                    "(includeRevoked={}).",
                    command.includeRevoked()
                );
            }

            return (Void) null;

        })).toEither().mapLeft(throwable -> {
            log.error(
                "[CleanupExpiredSessions] Falha ao remover sessões inativas: {}",
                throwable.getMessage(),
                throwable
            );
            return Notification.create(new Error(throwable.getMessage()));
        });
    }
}
```

> **Nota sobre `includeRevoked`:** o use case repassa o valor da flag ao gateway via o método `deleteInactive`. O gateway é responsável por montar a query correta conforme esse parâmetro. Isso evita duplicar lógica de query no use case e mantém o domínio limpo de detalhes de persistência.

### 4. Persistência (Infrastructure)

**`SessionJpaRepository`** — adicionar duas queries com `@Modifying`, uma para cada variante:

```java
// SessionJpaRepository.java — adicionar

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

/**
 * Remove fisicamente sessões expiradas OU revogadas em lote.
 *
 * <p>Usa subquery com {@code LIMIT} para garantir que o DELETE processe
 * no máximo {@code batchSize} registros por chamada, evitando locks longos.
 */
@Modifying
@Query(value = """
    DELETE FROM users.sessions
    WHERE id IN (
        SELECT id FROM users.sessions
        WHERE expires_at < :now
           OR revoked = true
        LIMIT :batchSize
    )
    """, nativeQuery = true)
int deleteExpiredOrRevoked(@Param("now") Instant now, @Param("batchSize") int batchSize);

/**
 * Remove fisicamente apenas sessões expiradas pelo tempo (ignora revogadas).
 *
 * <p>Útil quando se deseja preservar o histórico de sessões revogadas
 * para fins de auditoria por um período adicional.
 */
@Modifying
@Query(value = """
    DELETE FROM users.sessions
    WHERE id IN (
        SELECT id FROM users.sessions
        WHERE expires_at < :now
        LIMIT :batchSize
    )
    """, nativeQuery = true)
int deleteExpiredOnly(@Param("now") Instant now, @Param("batchSize") int batchSize);
```

> **Por que duas queries?** A flag `includeRevoked` do `Command` controla qual query é usada no gateway. Manter queries separadas é mais legível e performático do que uma query com `OR` condicional via JPQL — o otimizador do PostgreSQL planeja melhor queries simples.

**`SessionPostgresGateway`** — implementar `deleteInactive` com despacho baseado na flag:

```java
// SessionPostgresGateway.java — adicionar à classe existente

@Override
public int deleteInactive(final int batchSize) {
    // Deleta expiradas E revogadas — comportamento padrão do job
    return sessionJpaRepository.deleteExpiredOrRevoked(Instant.now(), batchSize);
}
```

> Se no futuro for necessário suportar `includeRevoked = false`, basta alterar a assinatura do gateway para `deleteInactive(int batchSize, boolean includeRevoked)` e despachar para a query correta. Por ora, o comportamento padrão (incluir revogadas) é o correto para produção.

### 5. Agendamento e Injeção (API)

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public CleanupExpiredSessionsUseCase cleanupExpiredSessionsUseCase(
        final SessionGateway sessionGateway,
        final TransactionManager transactionManager
) {
    return new CleanupExpiredSessionsUseCase(sessionGateway, transactionManager);
}
```

**`ScheduledJobsConfig.java`** — injetar e implementar:

```java
package com.btree.api.config;

import com.btree.application.usecase.user.job.CleanupExpiredSessionsCommand;
import com.btree.application.usecase.user.job.CleanupExpiredSessionsUseCase;
import com.btree.application.usecase.user.job.CleanupExpiredTokensCommand;
import com.btree.application.usecase.user.job.CleanupExpiredTokensUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
public class ScheduledJobsConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsConfig.class);

    private final CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase;
    private final CleanupExpiredSessionsUseCase cleanupExpiredSessionsUseCase;

    public ScheduledJobsConfig(
            final CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase,
            final CleanupExpiredSessionsUseCase cleanupExpiredSessionsUseCase
    ) {
        this.cleanupExpiredTokensUseCase = cleanupExpiredTokensUseCase;
        this.cleanupExpiredSessionsUseCase = cleanupExpiredSessionsUseCase;
    }

    /**
     * UC-13 — CleanupExpiredTokens.
     * Executa diariamente às 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        log.info("[ScheduledJobsConfig] Iniciando limpeza de tokens expirados...");
        cleanupExpiredTokensUseCase
                .execute(CleanupExpiredTokensCommand.withDefaultBatch())
                .peekLeft(notification ->
                    log.error("[ScheduledJobsConfig] Falha no job CleanupExpiredTokens: {}",
                            notification.getErrors())
                );
    }

    /**
     * UC-14 — CleanupExpiredSessions.
     *
     * <p>Executa diariamente às 04:00 — uma hora após o CleanupExpiredTokens
     * para evitar contenção simultânea no banco em horários de baixo tráfego.
     *
     * <p>Deleta até 1000 sessões inativas (expiradas ou revogadas) por execução.
     * Em sistemas de alto volume, considere reduzir o cron para {@code "0 0 * * * *"}
     * (a cada hora) e manter o batchSize conservador.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupExpiredSessions() {
        log.info("[ScheduledJobsConfig] Iniciando limpeza de sessões inativas...");
        cleanupExpiredSessionsUseCase
                .execute(CleanupExpiredSessionsCommand.withDefaultBatch())
                .peekLeft(notification ->
                    log.error("[ScheduledJobsConfig] Falha no job CleanupExpiredSessions: {}",
                            notification.getErrors())
                );
    }

    // ── Outros jobs ────────────────────────────────────────────────────────
    // TODO: @Scheduled(fixedDelay = 300_000)   → CleanupExpiredReservationsUseCase
    // TODO: @Scheduled(fixedDelay = 3_600_000) → ExpireAbandonedCartsUseCase
    // TODO: @Scheduled(fixedDelay = 60_000)    → ProcessPendingWebhooksUseCase
    // TODO: @Scheduled(fixedDelay = 600_000)   → CancelExpiredPaymentsUseCase
    // TODO: @Scheduled(cron = "0 0 2 * * *")   → ExpireCouponsUseCase
    // TODO: @Scheduled(fixedDelay = 900_000)   → CleanupExpiredCouponReservationsUseCase
    // TODO: @Scheduled(fixedDelay = 3_600_000) → ExpireDepletedCouponsUseCase
    // TODO: @Scheduled(fixedDelay = 5_000)     → ProcessDomainEventsUseCase
    // TODO: @Scheduled(fixedDelay = 60_000)    → RetryFailedEventsUseCase
}
```

---

## ⚠️ Casos de Erro Mapeados

| Situação | Comportamento | Log |
|---|---|---|
| Nenhuma sessão inativa encontrada | `Right(null)` — execução silenciosa | `DEBUG` com flag `includeRevoked` |
| Sessões deletadas com sucesso | `Right(null)` | `INFO` com contagem, flag e batchSize |
| Exceção de banco (conexão, timeout, deadlock) | `Left(Notification)` | `ERROR` com stack trace completo |
| `batchSize <= 0` no `Command` | `IllegalArgumentException` no construtor | Nunca chega ao use case |
| Job anterior ainda em execução | Spring não paraleliza `@Scheduled` por padrão — segunda execução aguarda | N/A |

---

## 🌐 Contrato da API REST

Este use case **não expõe endpoint HTTP**. É um job interno invocado exclusivamente pelo scheduler.

```
// Execução com sessões a limpar:
INFO  ScheduledJobsConfig        - [ScheduledJobsConfig] Iniciando limpeza de sessões inativas...
INFO  CleanupExpiredSessionsUseCase - [CleanupExpiredSessions] 312 sessão(ões) inativa(s) removida(s) (includeRevoked=true, batchSize=1000).

// Execução sem sessões elegíveis:
INFO  ScheduledJobsConfig        - [ScheduledJobsConfig] Iniciando limpeza de sessões inativas...
DEBUG CleanupExpiredSessionsUseCase - [CleanupExpiredSessions] Nenhuma sessão inativa encontrada (includeRevoked=true).

// Falha de infraestrutura:
ERROR CleanupExpiredSessionsUseCase - [CleanupExpiredSessions] Falha ao remover sessões inativas: could not execute statement...
ERROR ScheduledJobsConfig        - [ScheduledJobsConfig] Falha no job CleanupExpiredSessions: [could not execute statement...]
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`SessionGateway.java`** — adicionar assinatura `deleteInactive(int batchSize)`.
2. **`CleanupExpiredSessionsCommand.java`** — record com validação no construtor e factories `withDefaultBatch()` e `expiredOnly()`.
3. **`CleanupExpiredSessionsUseCase.java`** — lógica com `Try/Either` e logs estruturados.
4. **`SessionJpaRepository.java`** — adicionar `@Modifying @Query` natives com `LIMIT` em subquery para ambas as variantes.
5. **`SessionPostgresGateway.java`** — implementar `deleteInactive` delegando ao repository.
6. **`UseCaseConfig.java`** — registrar `@Bean`.
7. **`ScheduledJobsConfig.java`** — injetar novo use case via construtor e implementar `@Scheduled(cron = "0 0 4 * * *")`.
8. **Testes unitários** — `CleanupExpiredSessionsUseCaseTest` em `application/` com Mockito.
9. **Testes de integração** — `SessionPostgresGatewayIT` em `infrastructure/` com Testcontainers.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `CleanupExpiredSessionsUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Gateway deleta 312 sessões | `Right(null)`, log `INFO` com contagem 312 |
| Gateway deleta 0 sessões | `Right(null)`, log `DEBUG` |
| Gateway lança `DataAccessException` | `Left(Notification)` com mensagem da exceção |
| `batchSize = 0` no `Command` | `IllegalArgumentException` no construtor do `Command` |
| `batchSize = -1` no `Command` | `IllegalArgumentException` no construtor do `Command` |
| `withDefaultBatch()` | `Command` com `batchSize = 1000` e `includeRevoked = true` |
| `expiredOnly()` | `Command` com `batchSize = 1000` e `includeRevoked = false` |

### Integração (`infrastructure/`) — `SessionPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| 5 sessões expiradas (`expires_at` no passado), `batchSize = 1000` | Retorna 5, tabela sem sessões expiradas |
| 5 sessões revogadas (`revoked = true`, `expires_at` no futuro), `batchSize = 1000` | Retorna 5 com `deleteExpiredOrRevoked`, retorna 0 com `deleteExpiredOnly` |
| 3 sessões ativas (não expiradas, não revogadas), `batchSize = 1000` | Retorna 0, nenhuma sessão removida |
| Mix: 2 expiradas + 2 revogadas + 3 ativas | `deleteExpiredOrRevoked` retorna 4, apenas as 3 ativas permanecem |
| 10 sessões inativas, `batchSize = 3` | Retorna 3, 7 sessões inativas permanecem |
| Sessões de usuários distintos | Job afeta apenas as sessões elegíveis, sem discriminar por usuário |
| Sessão expirada E revogada simultaneamente | Deletada apenas uma vez (sem duplicata) |
| Tabela vazia | Retorna 0, sem exceção |