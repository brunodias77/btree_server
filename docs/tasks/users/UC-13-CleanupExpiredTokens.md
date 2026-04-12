# Task: UC-13 — CleanupExpiredTokens

## 📋 Resumo

Job agendado que remove fisicamente (ou invalida) tokens expirados da tabela `users.user_tokens`. Tokens do tipo `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `TWO_FACTOR`, `TWO_FACTOR_SETUP`, entre outros, acumulam na tabela ao longo do tempo — a maioria nunca sendo utilizada. Sem limpeza periódica, a tabela cresce indefinidamente, degradando performance de queries de lookup por `token_hash` e `user_id`.

## 🎯 Objetivo

Implementar um `UnitUseCase<Void>` do tipo `[JOB]` que deleta fisicamente todos os registros de `users.user_tokens` onde `expires_at < NOW()`, executado automaticamente via `@Scheduled` no `ScheduledJobsConfig`. A limpeza deve ser atômica, executar em lote com limite configurável para não travar a tabela, e registrar métricas básicas via log.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `BAIXA`
- **Endpoint:** Nenhum — executado exclusivamente como job agendado (`@Scheduled`)
- **Tabelas do Banco:** `users.user_tokens`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/UserTokenGateway.java` — **alterar**: adicionar método `deleteExpired(int batchSize)`

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/job/CleanupExpiredTokensCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/job/CleanupExpiredTokensUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/UserTokenJpaRepository.java` — **alterar**: adicionar query de deleção em lote
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/UserTokenPostgresGateway.java` — **alterar**: implementar `deleteExpired`

### `api`

1. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean` do novo use case
2. `api/src/main/java/com/btree/api/config/ScheduledJobsConfig.java` — **alterar**: descomentar e implementar o `@Scheduled` para `CleanupExpiredTokens`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway (Domain)

Adicionar no `UserTokenGateway` o método de deleção em lote:

```java
// UserTokenGateway.java — adicionar

/**
 * Remove fisicamente tokens expirados em lote.
 *
 * <p>O {@code batchSize} limita o número de registros deletados por execução,
 * evitando locks longos em tabelas grandes. Para limpeza completa, o job
 * pode ser executado repetidamente até que o retorno seja 0.
 *
 * @param batchSize limite máximo de registros a deletar nesta execução
 * @return número de registros efetivamente deletados
 */
int deleteExpired(int batchSize);
```

> **Decisão de design:** deleção física (`DELETE`) e não soft-delete, pois tokens expirados não têm valor de auditoria — são dados operacionais descartáveis. Tokens já utilizados (`used_at IS NOT NULL`) também são elegíveis para remoção após expiração.

### 2. Contrato de Entrada/Saída (Application)

**`CleanupExpiredTokensCommand`** — carrega apenas o `batchSize`, permitindo que o scheduler configure o tamanho do lote sem alterar o use case:

```java
package com.btree.application.usecase.user.job;

/**
 * Comando de entrada para UC-13 — CleanupExpiredTokens.
 *
 * @param batchSize número máximo de tokens a deletar por execução.
 *                  Valor padrão recomendado: 500.
 */
public record CleanupExpiredTokensCommand(int batchSize) {

    public CleanupExpiredTokensCommand {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("'batchSize' deve ser maior que zero");
        }
    }

    /** Factory com valor padrão seguro para uso no scheduler. */
    public static CleanupExpiredTokensCommand withDefaultBatch() {
        return new CleanupExpiredTokensCommand(500);
    }
}
```

Não há `Output` — o use case implementa `UnitUseCase<CleanupExpiredTokensCommand>` retornando `Either<Notification, Void>`. Métricas são emitidas via log dentro do use case.

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.job;

import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vavr.API.Try;

/**
 * Caso de uso UC-13 — CleanupExpiredTokens [JOB].
 *
 * <p>Remove fisicamente tokens expirados de {@code users.user_tokens}
 * em lotes para evitar locks prolongados. Deve ser executado
 * periodicamente via {@code @Scheduled} no {@code ScheduledJobsConfig}.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Deleta em lote até {@code batchSize} tokens com {@code expires_at < NOW()}.</li>
 *   <li>Registra via log o número de registros removidos.</li>
 *   <li>Retorna {@code Right(null)} em caso de sucesso ou {@code Left(Notification)}
 *       se a infraestrutura lançar exceção.</li>
 * </ol>
 *
 * <p><b>Idempotência:</b> executar quando não há tokens expirados retorna
 * {@code Right(null)} com log de 0 registros deletados — sem efeito colateral.
 */
public class CleanupExpiredTokensUseCase implements UnitUseCase<CleanupExpiredTokensCommand> {

    private static final Logger log = LoggerFactory.getLogger(CleanupExpiredTokensUseCase.class);

    private final UserTokenGateway userTokenGateway;
    private final TransactionManager transactionManager;

    public CleanupExpiredTokensUseCase(
            final UserTokenGateway userTokenGateway,
            final TransactionManager transactionManager
    ) {
        this.userTokenGateway = userTokenGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(final CleanupExpiredTokensCommand command) {
        return Try(() -> transactionManager.execute(() -> {
            final int deleted = userTokenGateway.deleteExpired(command.batchSize());

            if (deleted > 0) {
                log.info("[CleanupExpiredTokens] {} token(s) expirado(s) removido(s).", deleted);
            } else {
                log.debug("[CleanupExpiredTokens] Nenhum token expirado encontrado.");
            }

            return (Void) null;
        })).toEither().mapLeft(throwable -> {
            log.error("[CleanupExpiredTokens] Falha ao remover tokens expirados: {}", throwable.getMessage(), throwable);
            return Notification.create(new com.btree.shared.validation.Error(throwable.getMessage()));
        });
    }
}
```

> **Por que `Try` sem `Notification` prévia?** Jobs não recebem input do usuário — não há validação de negócio acumulável. O único caminho de `Left` é uma exceção de infraestrutura, capturada pelo `Try(...).toEither()`. O `batchSize` é validado no construtor do `Command`, falhando antes do use case ser invocado.

### 4. Persistência (Infrastructure)

**`UserTokenJpaRepository`** — adicionar query de deleção em lote com `@Modifying`:

```java
// UserTokenJpaRepository.java — adicionar

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

/**
 * Remove fisicamente tokens expirados (expires_at anterior ao instante fornecido).
 *
 * <p>A cláusula {@code WHERE id IN (SELECT id FROM ... LIMIT :batchSize)} garante
 * que o DELETE respeite o limite de lote sem travar a tabela inteira.
 * Funciona corretamente com PostgreSQL — o subselect é avaliado antes do DELETE.
 */
@Modifying
@Query(value = """
    DELETE FROM users.user_tokens
    WHERE id IN (
        SELECT id FROM users.user_tokens
        WHERE expires_at < :now
        LIMIT :batchSize
    )
    """, nativeQuery = true)
int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
```

> **Native query vs JPQL:** JPQL não suporta `LIMIT` em subqueries de forma portável. A native query com `LIMIT` é a abordagem correta aqui, e é segura pois os parâmetros são tipados (`Instant`, `int`) — sem risco de SQL injection.

**`UserTokenPostgresGateway`** — implementar o novo método:

```java
// UserTokenPostgresGateway.java — adicionar

@Override
public int deleteExpired(final int batchSize) {
    return userTokenJpaRepository.deleteExpiredBatch(Instant.now(), batchSize);
}
```

O método herda a anotação `@Transactional` da classe — correto para operações de escrita. Não é necessário `@Transactional(readOnly = true)` aqui.

### 5. Agendamento e Injeção (API)

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase(
        final UserTokenGateway userTokenGateway,
        final TransactionManager transactionManager
) {
    return new CleanupExpiredTokensUseCase(userTokenGateway, transactionManager);
}
```

**`ScheduledJobsConfig.java`** — descomentar e implementar:

```java
package com.btree.api.config;

import com.btree.application.usecase.user.job.CleanupExpiredTokensCommand;
import com.btree.application.usecase.user.job.CleanupExpiredTokensUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Agenda os jobs periódicos da aplicação via {@code @Scheduled}.
 *
 * <p>Cada método delega para um Use Case do tipo JOB localizado em
 * {@code application/<contexto>/job/}. Nenhuma lógica de negócio reside aqui.
 */
@Configuration(proxyBeanMethods = false)
public class ScheduledJobsConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsConfig.class);

    private final CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase;

    public ScheduledJobsConfig(final CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase) {
        this.cleanupExpiredTokensUseCase = cleanupExpiredTokensUseCase;
    }

    /**
     * UC-13 — CleanupExpiredTokens.
     *
     * <p>Executa diariamente às 03:00 (horário do servidor).
     * Deleta até 500 tokens expirados por execução — se a tabela acumular
     * mais, o job do dia seguinte continuará a limpeza.
     *
     * <p>Para limpeza agressiva em ambiente de carga, reduza o {@code cron}
     * para {@code "0 0 * * * *"} (a cada hora) e aumente o {@code batchSize}.
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

    // ── Outros jobs (adicionar conforme implementados) ─────────────────────
    // TODO: @Scheduled(cron = "0 0 4 * * *") → CleanupExpiredSessionsUseCase
    // TODO: @Scheduled(fixedDelay = 300_000)  → CleanupExpiredReservationsUseCase
    // TODO: @Scheduled(fixedDelay = 3_600_000) → ExpireAbandonedCartsUseCase
    // TODO: @Scheduled(fixedDelay = 60_000)   → ProcessPendingWebhooksUseCase
    // TODO: @Scheduled(fixedDelay = 600_000)  → CancelExpiredPaymentsUseCase
    // TODO: @Scheduled(cron = "0 0 2 * * *")  → ExpireCouponsUseCase
    // TODO: @Scheduled(fixedDelay = 900_000)  → CleanupExpiredCouponReservationsUseCase
    // TODO: @Scheduled(fixedDelay = 3_600_000) → ExpireDepletedCouponsUseCase
    // TODO: @Scheduled(fixedDelay = 5_000)    → ProcessDomainEventsUseCase
    // TODO: @Scheduled(fixedDelay = 60_000)   → RetryFailedEventsUseCase
}
```

> **Por que `@Scheduled` no `api/` e não em `infrastructure/`?** O `ScheduledJobsConfig` é o ponto de entrada dos jobs, assim como os `Controller`s são o ponto de entrada das requisições HTTP. Ambos vivem no `api/` — responsável por orquestrar a invocação dos use cases.

---

## ⚠️ Casos de Erro Mapeados

| Situação | Comportamento | Log |
|---|---|---|
| Nenhum token expirado encontrado | `Right(null)` — execução silenciosa | `DEBUG` com mensagem informativa |
| Tokens deletados com sucesso | `Right(null)` | `INFO` com contagem de registros |
| Exceção de banco (conexão, timeout) | `Left(Notification)` | `ERROR` com stack trace completo |
| `batchSize <= 0` no `Command` | `IllegalArgumentException` no construtor do `Command` | Nunca chega ao use case |

Jobs não têm usuário final aguardando resposta — falhas são absorvidas, logadas e a próxima execução tentará novamente. Não há propagação de exceção que derrube o scheduler.

---

## 🌐 Contrato da API REST

Este use case **não expõe endpoint HTTP**. É um job interno invocado exclusivamente pelo scheduler.

Para monitoramento, o Spring Actuator expõe métricas de agendamento em `/actuator/scheduledtasks` (quando habilitado). O log é a principal forma de observabilidade.

```
// Log de execução bem-sucedida:
INFO  ScheduledJobsConfig - [ScheduledJobsConfig] Iniciando limpeza de tokens expirados...
INFO  CleanupExpiredTokensUseCase - [CleanupExpiredTokens] 47 token(s) expirado(s) removido(s).

// Log quando não há nada a limpar:
INFO  ScheduledJobsConfig - [ScheduledJobsConfig] Iniciando limpeza de tokens expirados...
DEBUG CleanupExpiredTokensUseCase - [CleanupExpiredTokens] Nenhum token expirado encontrado.

// Log de falha de infraestrutura:
ERROR CleanupExpiredTokensUseCase - [CleanupExpiredTokens] Falha ao remover tokens expirados: ...
ERROR ScheduledJobsConfig - [ScheduledJobsConfig] Falha no job CleanupExpiredTokens: [...]
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`UserTokenGateway.java`** — adicionar assinatura `deleteExpired(int batchSize)`.
2. **`CleanupExpiredTokensCommand.java`** — record com validação no construtor e factory `withDefaultBatch()`.
3. **`CleanupExpiredTokensUseCase.java`** — lógica com `Try/Either` e logs estruturados.
4. **`UserTokenJpaRepository.java`** — adicionar `@Modifying @Query` native com `LIMIT` em subquery.
5. **`UserTokenPostgresGateway.java`** — implementar `deleteExpired` delegando ao repository.
6. **`UseCaseConfig.java`** — registrar `@Bean`.
7. **`ScheduledJobsConfig.java`** — injetar use case via construtor e implementar `@Scheduled`.
8. **Testes unitários** — `CleanupExpiredTokensUseCaseTest` em `application/` com Mockito.
9. **Testes de integração** — `UserTokenPostgresGatewayIT` em `infrastructure/` com Testcontainers.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `CleanupExpiredTokensUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Gateway deleta 47 tokens | `Right(null)`, log `INFO` com contagem 47 |
| Gateway deleta 0 tokens | `Right(null)`, log `DEBUG` |
| Gateway lança `DataAccessException` | `Left(Notification)` com mensagem da exceção |
| `batchSize = 0` no `Command` | `IllegalArgumentException` lançada no construtor do `Command` |
| `batchSize = -1` no `Command` | `IllegalArgumentException` lançada no construtor do `Command` |
| `withDefaultBatch()` | `Command` com `batchSize = 500` |

### Integração (`infrastructure/`) — `UserTokenPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| 10 tokens expirados, `batchSize = 500` | Retorna 10, tabela fica vazia de expirados |
| 10 tokens expirados, `batchSize = 3` | Retorna 3, 7 tokens expirados permanecem |
| 5 tokens válidos (não expirados), `batchSize = 500` | Retorna 0, nenhum token removido |
| Mix: 3 expirados + 2 válidos, `batchSize = 500` | Retorna 3, apenas os 2 válidos permanecem |
| Tokens já utilizados (`used_at IS NOT NULL`) mas expirados | São deletados normalmente |
| Tabela vazia | Retorna 0, sem exceção |
| Todos os tipos de token (`EMAIL_VERIFICATION`, `PASSWORD_RESET`, `TWO_FACTOR`, etc.) | Todos os expirados são deletados independentemente do tipo |