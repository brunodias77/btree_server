# Task: UC-19 — DeleteAddress

## 📋 Resumo

Permite que o usuário autenticado remova um endereço de entrega cadastrado em sua conta via soft delete — preenchendo `deleted_at` sem deletar o registro fisicamente do banco. Isso preserva referências históricas em pedidos já realizados que apontam para o endereço, mantendo integridade referencial e rastreabilidade de auditoria.

## 🎯 Objetivo

Implementar o endpoint `DELETE /api/v1/users/me/addresses/{id}` que, dado o `addressId` no path e o `userId` extraído do JWT, valida posse, aplica soft delete no aggregate e persiste. Retorna `204 No Content` em sucesso. Inclui regra de negócio crítica: o endereço padrão do usuário não pode ser deletado enquanto houver outros endereços ativos — o usuário deve primeiro designar outro endereço como padrão.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `DELETE /api/v1/users/me/addresses/{id}`
- **Tabelas do Banco:** `users.addresses`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/error/AddressError.java` — **alterar**: adicionar `CANNOT_DELETE_DEFAULT_ADDRESS`
2. `domain/src/main/java/com/btree/domain/user/gateway/AddressGateway.java` — **alterar**: adicionar `countActiveByUserIdExcluding(UserId, AddressId)`

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/address/DeleteAddressCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/address/DeleteAddressUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/AddressJpaRepository.java` — **alterar**: adicionar query `countActiveByUserIdExcludingId`
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/AddressPostgresGateway.java` — **alterar**: implementar `countActiveByUserIdExcluding`

### `api`

1. `api/src/main/java/com/btree/api/user/address/AddressController.java` — **alterar**: adicionar endpoint `DELETE /{id}`
2. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)

**`AddressError.java`** — adicionar constante de regra de negócio:

```java
// AddressError.java — adicionar

/**
 * Impede deleção do endereço padrão quando há outros endereços ativos.
 * O usuário deve primeiro designar outro endereço como padrão (UC-20).
 * Permite deleção do padrão apenas se for o único endereço ativo.
 */
public static final Error CANNOT_DELETE_DEFAULT_ADDRESS =
    new Error("Não é possível remover o endereço padrão. " +
              "Defina outro endereço como padrão antes de remover este.");
```

**`AddressGateway.java`** — adicionar método para contagem excluindo o próprio endereço:

```java
// AddressGateway.java — adicionar

/**
 * Conta endereços ativos do usuário excluindo o endereço informado.
 *
 * <p>Usado na regra de negócio de deleção do endereço padrão:
 * se o resultado for 0, o endereço é o único ativo e pode ser deletado
 * mesmo sendo o padrão. Se for > 0, a deleção do padrão é bloqueada.
 *
 * @param userId    ID do usuário proprietário
 * @param excludeId ID do endereço a excluir da contagem
 * @return número de endereços ativos excluindo {@code excludeId}
 */
long countActiveByUserIdExcluding(UserId userId, AddressId excludeId);
```

**`Address.java`** — verificar que `softDelete()` e `isDeleted()` existem do UC-17:

```java
// Address.java — verificar presença (implementado no UC-17)

public void softDelete() {
    this.deletedAt = java.time.Instant.now();
    this.updatedAt = java.time.Instant.now();
}

public boolean isDeleted() {
    return this.deletedAt != null;
}
```

Nenhuma adição necessária na entidade — `softDelete()` já existe.

### 2. Contrato de Entrada (Application)

**`DeleteAddressCommand.java`**:

```java
package com.btree.application.usecase.user.address;

/**
 * Comando de entrada para UC-19 — DeleteAddress.
 *
 * <p>Não há Output — o use case implementa {@code UnitUseCase}
 * e retorna {@code Either<Notification, Void>}.
 * O controller responde com {@code 204 No Content}.
 *
 * @param userId    ID do usuário autenticado (extraído do JWT)
 * @param addressId ID do endereço a remover
 */
public record DeleteAddressCommand(String userId, String addressId) {}
```

> Não há `DeleteAddressOutput` — o use case implementa `UnitUseCase<DeleteAddressCommand>` retornando `Either<Notification, Void>`. Soft delete bem-sucedido não produz dado relevante para o cliente além do status `204`.

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.error.AddressError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UnitUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-19 — DeleteAddress [CMD P0].
 *
 * <p>Aplica soft delete em um endereço do usuário autenticado,
 * preenchendo {@code deleted_at} sem remover o registro fisicamente.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O endereço deve existir e não estar já deletado.</li>
 *   <li>O endereço deve pertencer ao {@code userId} do JWT.</li>
 *   <li>Se o endereço é o padrão ({@code isDefault = true}) e existem
 *       outros endereços ativos, a operação é bloqueada — o usuário deve
 *       primeiro designar outro como padrão via UC-20.</li>
 *   <li>Se o endereço é o padrão e é o único endereço ativo, a deleção
 *       é permitida (usuário ficará sem endereço padrão).</li>
 * </ul>
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato de {@code userId} e {@code addressId}.</li>
 *   <li>Busca o endereço pelo {@code addressId}.</li>
 *   <li>Verifica existência e não-deleção prévia.</li>
 *   <li>Verifica posse — segurança crítica.</li>
 *   <li>Se padrão, verifica se há outros endereços ativos — bloqueia se sim.</li>
 *   <li>Chama {@code address.softDelete()} para mutar o aggregate.</li>
 *   <li>Persiste via {@code addressGateway.update()} dentro da transação.</li>
 *   <li>Retorna {@code Right(null)}.</li>
 * </ol>
 */
public class DeleteAddressUseCase implements UnitUseCase<DeleteAddressCommand> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public DeleteAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, Void> execute(final DeleteAddressCommand command) {
        final var notification = Notification.create();

        // 1. Validar presença e formato do userId
        if (command.userId() == null || command.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(command.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        // 2. Validar presença e formato do addressId
        if (command.addressId() == null || command.addressId().isBlank()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final AddressId addressId;
        try {
            addressId = AddressId.from(UUID.fromString(command.addressId()));
        } catch (IllegalArgumentException e) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        // 3. Buscar o endereço
        final var addressOpt = addressGateway.findById(addressId);
        if (addressOpt.isEmpty()) {
            notification.append(AddressError.ADDRESS_NOT_FOUND);
            return Left(notification);
        }

        final var address = addressOpt.get();

        // 4. Verificar soft-delete prévio
        if (address.isDeleted()) {
            notification.append(AddressError.ADDRESS_ALREADY_DELETED);
            return Left(notification);
        }

        // 5. Verificar posse — segurança crítica
        if (!address.getUserId().getValue().equals(userId.getValue())) {
            notification.append(AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER);
            return Left(notification);
        }

        // 6. Regra de negócio: endereço padrão não pode ser deletado
        //    se existirem outros endereços ativos
        if (address.isDefault()) {
            final long otherActiveCount =
                addressGateway.countActiveByUserIdExcluding(userId, addressId);
            if (otherActiveCount > 0) {
                notification.append(AddressError.CANNOT_DELETE_DEFAULT_ADDRESS);
                return Left(notification);
            }
        }

        // 7. Aplicar soft delete e persistir em transação
        return Try(() -> transactionManager.execute(() -> {
            address.softDelete();
            addressGateway.update(address);
            return (Void) null;
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Por que `UnitUseCase` e não `UseCase`?** Soft delete não produz dado de retorno relevante. O cliente recebe `204 No Content` — qualquer dado retornado seria descartado. `UnitUseCase<DeleteAddressCommand>` é a abstração correta para side-effects sem output.

### 4. Persistência (Infrastructure)

**`AddressJpaRepository.java`** — adicionar query de contagem com exclusão:

```java
// AddressJpaRepository.java — adicionar

/**
 * Conta endereços ativos do usuário excluindo o endereço informado.
 *
 * <p>Usado na regra de deleção do endereço padrão: se o resultado
 * for 0, o endereço é o único ativo e pode ser deletado sem restrição.
 */
@Query("""
    SELECT COUNT(a) FROM AddressJpaEntity a
    WHERE a.userId = :userId
      AND a.id <> :excludeId
      AND a.deletedAt IS NULL
    """)
long countActiveByUserIdExcludingId(
        @Param("userId") UUID userId,
        @Param("excludeId") UUID excludeId
);
```

**`AddressPostgresGateway.java`** — implementar o novo método do gateway:

```java
// AddressPostgresGateway.java — adicionar

@Override
@Transactional(readOnly = true)
public long countActiveByUserIdExcluding(final UserId userId, final AddressId excludeId) {
    return addressJpaRepository.countActiveByUserIdExcludingId(
            userId.getValue(),
            excludeId.getValue()
    );
}
```

A operação de soft delete em si é realizada pelo método `update()` já existente — `address.softDelete()` preenche `deletedAt` no aggregate e `update()` persiste via `updateFrom()` na JPA entity.

Verificar que `AddressJpaEntity.updateFrom` transfere corretamente o `deletedAt`:

```java
// AddressJpaEntity.java — verificar que updateFrom inclui:
public void updateFrom(final Address address) {
    // ... demais campos ...
    this.deletedAt = address.getDeletedAt(); // ← OBRIGATÓRIO para soft delete funcionar
    this.updatedAt = address.getUpdatedAt();
}
```

### 5. Roteamento e Injeção (API)

**`AddressController.java`** — adicionar endpoint `DELETE /{id}` e atualizar construtor:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.AddAddressCommand;
import com.btree.application.usecase.user.address.AddAddressUseCase;
import com.btree.application.usecase.user.address.DeleteAddressCommand;
import com.btree.application.usecase.user.address.DeleteAddressUseCase;
import com.btree.application.usecase.user.address.UpdateAddressCommand;
import com.btree.application.usecase.user.address.UpdateAddressUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de endereços do usuário autenticado.
 *
 * <p>Use cases mapeados:
 * <ul>
 *   <li>UC-17 AddAddress    — {@code POST /}</li>
 *   <li>UC-18 UpdateAddress — {@code PUT /{id}}</li>
 *   <li>UC-19 DeleteAddress — {@code DELETE /{id}}</li>
 *   <li>UC-20 SetDefault    — {@code PATCH /{id}/default} (próximo)</li>
 *   <li>UC-21 ListAddresses — {@code GET /} (próximo)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/users/me/addresses")
@Tag(name = "Addresses", description = "Gerenciamento de endereços de entrega do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddAddressUseCase addAddressUseCase;
    private final UpdateAddressUseCase updateAddressUseCase;
    private final DeleteAddressUseCase deleteAddressUseCase;

    public AddressController(
            final AddAddressUseCase addAddressUseCase,
            final UpdateAddressUseCase updateAddressUseCase,
            final DeleteAddressUseCase deleteAddressUseCase
    ) {
        this.addAddressUseCase    = addAddressUseCase;
        this.updateAddressUseCase = updateAddressUseCase;
        this.deleteAddressUseCase = deleteAddressUseCase;
    }

    // ── UC-17: AddAddress ─────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar endereço",
            description = "Adiciona um novo endereço de entrega. " +
                          "Se for o primeiro endereço, é automaticamente marcado como padrão."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Endereço cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public AddressResponse add(@Valid @RequestBody final AddAddressRequest request) {
        final String userId = currentUserId();

        return AddressResponse.from(
                addAddressUseCase.execute(new AddAddressCommand(
                        userId,
                        request.label(),
                        request.recipientName(),
                        request.street(),
                        request.number(),
                        request.complement(),
                        request.neighborhood(),
                        request.city(),
                        request.state(),
                        request.postalCode(),
                        request.country() != null ? request.country() : "BR",
                        request.isBillingAddress()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-18: UpdateAddress ──────────────────────────────────────────────

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Editar endereço",
            description = "Atualiza os dados de um endereço existente. " +
                          "Enviar payload completo — campos ausentes são gravados como null."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endereço atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, deletado ou não pertence ao usuário")
    })
    public UpdateAddressResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateAddressRequest request
    ) {
        final String userId = currentUserId();

        return UpdateAddressResponse.from(
                updateAddressUseCase.execute(new UpdateAddressCommand(
                        userId,
                        id,
                        request.label(),
                        request.recipientName(),
                        request.street(),
                        request.number(),
                        request.complement(),
                        request.neighborhood(),
                        request.city(),
                        request.state(),
                        request.postalCode(),
                        request.country() != null ? request.country() : "BR",
                        request.isBillingAddress()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-19: DeleteAddress ──────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remover endereço",
            description = "Aplica soft delete em um endereço do usuário autenticado. " +
                          "O registro é preservado no banco para manter histórico em pedidos anteriores. " +
                          "Não é possível remover o endereço padrão se houver outros endereços ativos — " +
                          "defina outro como padrão antes de remover."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Endereço removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, já removido, " +
                                                             "não pertence ao usuário ou é o endereço padrão " +
                                                             "com outros endereços ativos")
    })
    public void delete(@PathVariable final String id) {
        final String userId = currentUserId();

        deleteAddressUseCase
                .execute(new DeleteAddressCommand(userId, id))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
```

> **Por que `void delete()`?** O método retorna `void` porque o status `204 No Content` não carrega body. O Spring processa o `@ResponseStatus(HttpStatus.NO_CONTENT)` e a resposta não inclui payload. O `getOrElseThrow` garante que erros ainda disparam o `GlobalExceptionHandler` normalmente.

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public DeleteAddressUseCase deleteAddressUseCase(
        final AddressGateway addressGateway,
        final TransactionManager transactionManager
) {
    return new DeleteAddressUseCase(addressGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `UserError.USER_NOT_FOUND` | `userId` nulo, vazio ou UUID inválido no JWT | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_NOT_FOUND` | `addressId` nulo, UUID inválido ou endereço inexistente | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_ALREADY_DELETED` | Endereço com `deleted_at` preenchido (já removido) | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER` | `address.userId != userId` do JWT | `422 Unprocessable Entity` |
| `AddressError.CANNOT_DELETE_DEFAULT_ADDRESS` | Endereço é padrão e existem outros endereços ativos | `422 Unprocessable Entity` |
| `AuthenticationException` | JWT ausente ou inválido | `401 Unauthorized` |

> **Idempotência parcial:** se o endereço já está deletado, retorna `422 ADDRESS_ALREADY_DELETED` em vez de `204`. A operação não é idempotente por design — uma segunda chamada informa ao cliente que o endereço já havia sido removido, evitando bugs silenciosos no frontend.

---

## 🌐 Contrato da API REST

### Request

```http
DELETE /api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678
Authorization: Bearer <access_token>
```

Sem body.

### Response (Sucesso — 204 No Content)

Sem body.

### Response (Erro — 422 Endereço padrão com outros ativos)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Não é possível remover o endereço padrão. Defina outro endereço como padrão antes de remover este.",
  "errors": [
    "Não é possível remover o endereço padrão. Defina outro endereço como padrão antes de remover este."
  ],
  "timestamp": "2026-04-09T16:00:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

### Response (Erro — 422 Já removido)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço já foi removido",
  "errors": ["Endereço já foi removido"],
  "timestamp": "2026-04-09T16:00:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

### Response (Erro — 422 Endereço de outro usuário)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço não pertence ao usuário informado",
  "errors": ["Endereço não pertence ao usuário informado"],
  "timestamp": "2026-04-09T16:00:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

### Response (Erro — 401 Não autenticado)

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Autenticação necessária.",
  "timestamp": "2026-04-09T16:00:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`AddressError.java`** — adicionar `CANNOT_DELETE_DEFAULT_ADDRESS`.
2. **`AddressGateway.java`** — adicionar `countActiveByUserIdExcluding(UserId, AddressId)`.
3. **`AddressJpaRepository.java`** — adicionar `countActiveByUserIdExcludingId` com `@Query` JPQL.
4. **`AddressPostgresGateway.java`** — implementar `countActiveByUserIdExcluding` com `@Transactional(readOnly = true)`.
5. **`DeleteAddressCommand.java`** — record com `userId` e `addressId`.
6. **`DeleteAddressUseCase.java`** — lógica completa com verificação de posse, regra do padrão e `Either`.
7. **`UseCaseConfig.java`** — registrar `@Bean` do `DeleteAddressUseCase`.
8. **`AddressController.java`** — adicionar `deleteAddressUseCase` ao construtor e endpoint `DELETE /{id}`.
9. **Testes unitários** — `DeleteAddressUseCaseTest` em `application/` com Mockito.
10. **Testes de integração** — `AddressPostgresGatewayIT` em `infrastructure/` cobrindo `countActiveByUserIdExcluding`.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `DeleteAddressUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Endereço válido, não padrão, pertence ao usuário | `Right(null)` — soft delete aplicado, `update()` chamado |
| Endereço padrão — único endereço ativo do usuário | `Right(null)` — permitido pois não há outros (`count = 0`) |
| Endereço padrão — existem outros endereços ativos (`count = 2`) | `Left(Notification)` com `CANNOT_DELETE_DEFAULT_ADDRESS` |
| `userId` nulo | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` em branco | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `addressId` nulo | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` em branco | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` UUID inválido | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| Endereço não existe no gateway | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| Endereço já com `isDeleted() = true` | `Left(Notification)` com `AddressError.ADDRESS_ALREADY_DELETED` |
| Endereço pertence a outro usuário | `Left(Notification)` com `AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER` |
| Gateway `update()` lança exceção de infra | `Left(Notification)` via `Try().toEither()` |
| Verificação de posse ocorre antes da regra do padrão | `ADDRESS_BELONGS_TO_ANOTHER_USER` retornado antes de consultar `count` |

### Integração (`infrastructure/`) — `AddressPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| `countActiveByUserIdExcluding()` — usuário com 3 ativos, exclui 1 | Retorna `2` |
| `countActiveByUserIdExcluding()` — usuário com 1 ativo, exclui ele mesmo | Retorna `0` |
| `countActiveByUserIdExcluding()` — sem endereços ativos além do excluído | Retorna `0` |
| `countActiveByUserIdExcluding()` — endereços de outro usuário não contados | Retorna apenas os do `userId` informado |
| `countActiveByUserIdExcluding()` — endereços soft-deletados não contados | `deletedAt IS NULL` filtrado corretamente |
| `update()` com `deletedAt` preenchido | Campo `deleted_at` persiste no banco |
| `findById()` após soft delete | Retorna `Optional` com `deletedAt` preenchido (sem filtro de deleted) |
| `findByUserId()` após soft delete | Endereço deletado **não** aparece na lista (filtro `deletedAt IS NULL`) |
| Soft delete não apaga `userId`, `street`, `city`, `createdAt` | Campos imutáveis preservados |
| `updatedAt` maior que `createdAt` após soft delete | Timestamp atualizado corretamente |