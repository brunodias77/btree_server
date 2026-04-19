# Task: UC-12 — LogoutAllSessions

## 📋 Resumo

Revoga todas as sessões ativas de um usuário autenticado, invalidando todos os refresh tokens em uso. É útil quando o usuário suspeita de acesso não autorizado, troca de senha ou simplesmente quer encerrar todas as sessões em outros dispositivos.

## 🎯 Objetivo

Implementar o endpoint `POST /api/v1/users/me/sessions/revoke-all` que, dado o `userId` extraído do JWT, marca todas as sessões ativas do usuário como revogadas (`revoked = true`) em uma única transação atômica.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `MÉDIA`
- **Endpoint:** `POST /api/v1/users/me/sessions/revoke-all`
- **Tabelas do Banco:** `users.sessions`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/SessionGateway.java` — **alterar**: adicionar método `revokeAllByUserId(UserId userId)`

### `application`

1. `application/src/main/java/com/btree/application/usecase/auth/logout/LogoutAllSessionsCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/auth/logout/LogoutAllSessionsUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/SessionJpaRepository.java` — **alterar**: adicionar query de revogação em lote
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/SessionPostgresGateway.java` — **alterar**: implementar `revokeAllByUserId`

### `api`

1. `api/src/main/java/com/btree/api/user/auth/LogoutAllSessionsResponse.java` — **criar**
2. `api/src/main/java/com/btree/api/user/auth/AuthController.java` — **alterar**: adicionar endpoint `POST /sessions/revoke-all`
3. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean` do novo use case

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway (Domain)

Adicionar no `SessionGateway` o método que realiza a revogação em lote:

```java
// SessionGateway.java — adicionar
int revokeAllByUserId(UserId userId);
```

O retorno `int` representa a quantidade de sessões efetivamente revogadas — útil para o output sem necessidade de carregar entidades em memória.

### 2. Contrato de Entrada/Saída (Application)

**`LogoutAllSessionsCommand`** — transporta apenas o `userId` extraído do JWT pelo controller:

```java
package com.btree.application.usecase.auth.logout;

/**
 * Comando de entrada para UC-12 — LogoutAllSessions.
 *
 * @param userId ID do usuário autenticado, extraído do JWT pelo controller.
 */
public record LogoutAllSessionsCommand(String userId) {}
```

Não há `LogoutAllSessionsOutput` com dados de negócio relevantes — o use case implementa `UnitUseCase<LogoutAllSessionsCommand>` retornando `Either<Notification, Void>`. O controller devolverá `204 No Content`.

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.auth.logout;

import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-12 — LogoutAllSessions.
 *
 * <p>Revoga atomicamente todas as sessões ativas do usuário autenticado.
 * A operação é idempotente: se não houver sessões ativas, retorna sucesso
 * silenciosamente com {@code revokedCount = 0}.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Executa revogação em lote via {@code sessionGateway.revokeAllByUserId()} dentro de transação.</li>
 *   <li>Retorna {@code Right(null)} — 204 No Content no controller.</li>
 * </ol>
 *
 * <p><b>Segurança:</b> O token JWT do request ainda é válido até expirar
 * (stateless), mas nenhuma renovação via refresh token será possível.
 */
public class LogoutAllSessionsUseCase implements UnitUseCase<LogoutAllSessionsCommand> {

    private final SessionGateway sessionGateway;
    private final TransactionManager transactionManager;

    public LogoutAllSessionsUseCase(
            final SessionGateway sessionGateway,
            final TransactionManager transactionManager
    ) {
        this.sessionGateway = sessionGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(final LogoutAllSessionsCommand command) {
        final var notification = Notification.create();

        // 1. Validar presença do userId
        if (command.userId() == null || command.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        // 2. Converter e revogar em transação
        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(command.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        return Try(() -> transactionManager.execute(() -> {
            sessionGateway.revokeAllByUserId(userId);
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 4. Persistência (Infrastructure)

**`SessionJpaRepository`** — adicionar query de revogação em lote com `@Modifying`:

```java
// SessionJpaRepository.java — adicionar
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

@Modifying
@Query("""
    UPDATE SessionJpaEntity s
    SET s.revoked = true
    WHERE s.userId = :userId
      AND s.revoked = false
      AND s.expiresAt > :now
    """)
int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
```

A query filtra apenas sessões `revoked = false` e ainda não expiradas (`expiresAt > now`), evitando updates desnecessários em sessões já inativas. O `@Modifying` é obrigatório para queries de escrita no Spring Data JPA.

**`SessionPostgresGateway`** — implementar o novo método do gateway:

```java
// SessionPostgresGateway.java — adicionar
@Override
public int revokeAllByUserId(final UserId userId) {
    return sessionJpaRepository.revokeAllActiveByUserId(
            userId.getValue(),
            Instant.now()
    );
}
```

### 5. Roteamento e Injeção (API)

**`LogoutAllSessionsResponse`**:

```java
package com.btree.api.user.auth;

/**
 * DTO HTTP de saída para {@code POST /api/v1/users/me/sessions/revoke-all}.
 *
 * <p>Retorna a quantidade de sessões revogadas para feedback ao cliente.
 * O campo {@code message} fornece uma confirmação legível.
 */
public record LogoutAllSessionsResponse(String message) {

    public static LogoutAllSessionsResponse success() {
        return new LogoutAllSessionsResponse(
                "Todas as sessões foram revogadas com sucesso."
        );
    }
}
```

> **Nota de design:** o use case retorna `Void` (UnitUseCase), então o response de sucesso é construído diretamente no controller com `LogoutAllSessionsResponse.success()`. Não há output do use case para mapear.

**`AuthController`** — adicionar endpoint:

```java
// AuthController.java — adicionar import e endpoint

import com.btree.application.usecase.auth.logout.LogoutAllSessionsCommand;
import com.btree.application.usecase.auth.logout.LogoutAllSessionsUseCase;

// No construtor — adicionar parâmetro:
private final LogoutAllSessionsUseCase logoutAllSessionsUseCase;

// Endpoint:
@PostMapping("/sessions/revoke-all")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Revogar todas as sessões",
        description = "Invalida todos os refresh tokens ativos do usuário autenticado em todos os dispositivos. "
                + "O access token atual continua válido até expirar (stateless)."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todas as sessões revogadas com sucesso"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
})
@SecurityRequirement(name = "bearerAuth")
public LogoutAllSessionsResponse revokeAllSessions() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    final String userId = auth.getName();

    logoutAllSessionsUseCase.execute(new LogoutAllSessionsCommand(userId))
            .getOrElseThrow(n -> DomainException.with(n.getErrors()));

    return LogoutAllSessionsResponse.success();
}
```

> O endpoint não recebe `@RequestBody` — o `userId` vem do `SecurityContextHolder`, garantindo que o usuário só pode revogar suas próprias sessões.

**`UseCaseConfig`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public LogoutAllSessionsUseCase logoutAllSessionsUseCase(
        final SessionGateway sessionGateway,
        final TransactionManager transactionManager
) {
    return new LogoutAllSessionsUseCase(sessionGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio                             | Condição                                | Status HTTP Resultante      |
| ------------------------------------------- | --------------------------------------- | --------------------------- |
| `UserError.USER_NOT_FOUND`                  | `userId` ausente, nulo ou UUID inválido | `422 Unprocessable Entity`  |
| `AuthenticationException` (Spring Security) | JWT ausente ou inválido no header       | `401 Unauthorized`          |
| Exceção de infra (banco indisponível)       | Falha no `transactionManager.execute()` | `500 Internal Server Error` |

**Comportamento idempotente:** se o usuário não tiver nenhuma sessão ativa, a operação retorna `200 OK` normalmente com `revokedCount = 0` — sem erro, sem `404`.

---

## 🌐 Contrato da API REST

### Request

```http
POST /api/v1/auth/sessions/revoke-all
Authorization: Bearer <access_token>
```

Sem body.

### Response (Sucesso — 200 OK)

```json
{
  "message": "Todas as sessões foram revogadas com sucesso."
}
```

### Response (Erro — 401 Unauthorized)

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Autenticação necessária.",
  "timestamp": "2026-04-08T00:00:00Z",
  "path": "/api/v1/auth/sessions/revoke-all"
}
```

### Response (Erro — 422 Unprocessable Entity)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Usuário não encontrado",
  "errors": ["Usuário não encontrado"],
  "timestamp": "2026-04-08T00:00:00Z",
  "path": "/api/v1/auth/sessions/revoke-all"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`SessionGateway.java`** — adicionar assinatura `revokeAllByUserId(UserId userId)`.
2. **`LogoutAllSessionsCommand.java`** — record com `userId`.
3. **`LogoutAllSessionsUseCase.java`** — lógica com `Either` e `TransactionManager`.
4. **`SessionJpaRepository.java`** — adicionar `@Modifying @Query` para revogação em lote.
5. **`SessionPostgresGateway.java`** — implementar `revokeAllByUserId`.
6. **`UseCaseConfig.java`** — registrar `@Bean`.
7. **`LogoutAllSessionsResponse.java`** — record de resposta com `success()`.
8. **`AuthController.java`** — adicionar construtor atualizado e endpoint `revokeAllSessions()`.
9. **Testes unitários** — `LogoutAllSessionsUseCaseTest` em `application/` com Mockito (sem Spring).
10. **Testes de integração** — `SessionPostgresGatewayIT` em `infrastructure/` com Testcontainers verificando que sessões são marcadas como `revoked = true` e sessões já revogadas ou expiradas não são afetadas.

---

## 🧪 Cenários de Teste

### Unitários (`application/`)

| Cenário                            | Comportamento esperado                                |
| ---------------------------------- | ----------------------------------------------------- |
| `userId` válido com sessões ativas | `Right(null)` — gateway chamado com `UserId` correto  |
| `userId` nulo ou em branco         | `Left(Notification)` com `UserError.USER_NOT_FOUND`   |
| `userId` com formato UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND`   |
| Gateway lança exceção de infra     | `Left(Notification)` via `Try().toEither().mapLeft()` |
| Usuário sem sessões ativas         | `Right(null)` — idempotente, sem erro                 |

### Integração (`infrastructure/`)

| Cenário                                   | Verificação                                          |
| ----------------------------------------- | ---------------------------------------------------- |
| Usuário com 3 sessões ativas              | Após `revokeAllByUserId`, todas com `revoked = true` |
| Sessão já revogada pertencente ao usuário | Não é afetada (query filtra `revoked = false`)       |
| Sessão expirada pertencente ao usuário    | Não é afetada (query filtra `expiresAt > now`)       |
| Sessões de outro usuário                  | Não são afetadas (query filtra `userId`)             |
| Retorno do método                         | Igual ao número de sessões previamente ativas        |
