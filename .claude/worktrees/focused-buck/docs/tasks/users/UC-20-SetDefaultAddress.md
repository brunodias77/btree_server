# Task: UC-20 — SetDefaultAddress

## 📋 Resumo

Permite que o usuário autenticado designe um de seus endereços cadastrados como endereço padrão de entrega. O endereço padrão é pré-selecionado automaticamente no checkout, eliminando fricção no fluxo de compra. A operação é atômica: remove a marcação do padrão anterior e aplica no novo, garantindo que o usuário sempre tenha exatamente um endereço padrão ativo.

## 🎯 Objetivo

Implementar o endpoint `PATCH /api/v1/users/me/addresses/{id}/default` que, dado o `addressId` no path e o `userId` extraído do JWT, valida posse, executa a troca de padrão em transação única e retorna o endereço agora marcado como padrão. A operação deve ser idempotente — chamar com o endereço que já é padrão retorna sucesso sem efeitos colaterais.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `PATCH /api/v1/users/me/addresses/{id}/default`
- **Tabelas do Banco:** `users.addresses`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/AddressGateway.java` — **verificar**: confirmar que todos os métodos dos UCs anteriores existem; nenhuma adição necessária para este use case

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/address/SetDefaultAddressCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/address/SetDefaultAddressOutput.java` — **criar**
3. `application/src/main/java/com/btree/application/usecase/user/address/SetDefaultAddressUseCase.java` — **criar**

### `infrastructure`

1. Nenhum arquivo novo — todos os métodos necessários foram implementados nos UCs anteriores

### `api`

1. `api/src/main/java/com/btree/api/user/address/SetDefaultAddressResponse.java` — **criar**
2. `api/src/main/java/com/btree/api/user/address/AddressController.java` — **alterar**: adicionar endpoint `PATCH /{id}/default` e atualizar construtor
3. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway (Domain)

Verificar que `AddressGateway` possui todos os métodos necessários para este use case:

```java
// AddressGateway.java — verificar presença dos seguintes métodos (implementados nos UCs anteriores)

Optional<Address> findById(AddressId id);          // UC-17 — buscar endereço pelo ID
Address update(Address address);                    // UC-17 — persistir mutação
void clearDefaultByUserId(UserId userId);           // UC-17 — remover padrão anterior
```

Nenhuma adição ao gateway é necessária — o use case orquestra métodos já existentes.

**Verificar comportamento de `clearDefaultByUserId`:** executa `UPDATE ... SET is_default = false WHERE user_id = :userId AND is_default = true AND deleted_at IS NULL`. Essa query já foi implementada no UC-17 via `@Modifying @Query` no `AddressJpaRepository`.

### 2. Contrato de Entrada/Saída (Application)

**`SetDefaultAddressCommand.java`**:

```java
package com.btree.application.usecase.user.address;

/**
 * Comando de entrada para UC-20 — SetDefaultAddress.
 *
 * @param userId    ID do usuário autenticado (extraído do JWT)
 * @param addressId ID do endereço a marcar como padrão
 */
public record SetDefaultAddressCommand(String userId, String addressId) {}
```

**`SetDefaultAddressOutput.java`**:

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-20 — SetDefaultAddress.
 *
 * <p>Retorna o endereço completo após ser marcado como padrão,
 * permitindo que o cliente atualize seu estado local sem
 * necessidade de uma chamada adicional de leitura.
 */
public record SetDefaultAddressOutput(
        String id,
        String userId,
        String label,
        String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        boolean isBillingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static SetDefaultAddressOutput from(final Address address) {
        return new SetDefaultAddressOutput(
                address.getId().getValue().toString(),
                address.getUserId().getValue().toString(),
                address.getLabel(),
                address.getRecipientName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.isBillingAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.AddressError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-20 — SetDefaultAddress [CMD P0].
 *
 * <p>Marca um endereço como padrão de entrega, removendo a marcação
 * de qualquer outro endereço padrão do mesmo usuário em uma transação
 * atômica.
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>O endereço deve existir e não estar soft-deletado.</li>
 *   <li>O endereço deve pertencer ao {@code userId} do JWT.</li>
 *   <li>Se o endereço já é o padrão, a operação é idempotente —
 *       retorna sucesso sem modificações.</li>
 * </ul>
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato de {@code userId} e {@code addressId}.</li>
 *   <li>Busca o endereço pelo {@code addressId}.</li>
 *   <li>Verifica existência e não-deleção.</li>
 *   <li>Verifica posse — segurança crítica.</li>
 *   <li>Idempotência: se já é padrão, retorna {@code Right(output)} imediatamente.</li>
 *   <li>Em transação: remove padrão anterior via {@code clearDefaultByUserId},
 *       aplica {@code address.setAsDefault()} e persiste via {@code update}.</li>
 *   <li>Retorna {@link SetDefaultAddressOutput} com o endereço atualizado.</li>
 * </ol>
 *
 * <p><b>Atomicidade:</b> {@code clearDefaultByUserId} e {@code update} executam
 * dentro do mesmo {@code transactionManager.execute()} — se qualquer um falhar,
 * ambos são revertidos. Isso garante que nunca existam dois endereços padrão
 * ou nenhum após a operação.
 */
public class SetDefaultAddressUseCase implements UseCase<SetDefaultAddressCommand, SetDefaultAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public SetDefaultAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, SetDefaultAddressOutput> execute(final SetDefaultAddressCommand command) {
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

        // 4. Verificar soft-delete
        if (address.isDeleted()) {
            notification.append(AddressError.ADDRESS_ALREADY_DELETED);
            return Left(notification);
        }

        // 5. Verificar posse — segurança crítica
        if (!address.getUserId().getValue().equals(userId.getValue())) {
            notification.append(AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER);
            return Left(notification);
        }

        // 6. Idempotência — já é o padrão, retornar sucesso sem modificações
        if (address.isDefault()) {
            return io.vavr.control.Either.right(SetDefaultAddressOutput.from(address));
        }

        // 7. Troca atômica do padrão dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            // Remove padrão de todos os outros endereços do usuário
            addressGateway.clearDefaultByUserId(userId);

            // Marca este endereço como padrão
            address.setAsDefault();
            final var updated = addressGateway.update(address);

            return SetDefaultAddressOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Por que `clearDefaultByUserId` antes de `setAsDefault`?** A ordem importa: primeiro removemos o padrão anterior para garantir que em caso de falha parcial (improvável dentro da mesma transação, mas defensivamente correto) não haja dois padrões simultaneamente. O rollback da transação reverte ambas as operações em caso de exceção.

### 4. Persistência (Infrastructure)

Nenhum arquivo novo necessário. Todos os métodos usados por este use case foram implementados nos UCs anteriores:

| Método | Implementado em |
|---|---|
| `AddressGateway.findById` | UC-17 |
| `AddressGateway.update` | UC-17 |
| `AddressGateway.clearDefaultByUserId` | UC-17 |
| `AddressJpaRepository.clearDefaultByUserId` | UC-17 |
| `AddressPostgresGateway.clearDefaultByUserId` | UC-17 |

Verificar que `AddressJpaEntity.updateFrom` transfere `isDefault` e `updatedAt` corretamente:

```java
// AddressJpaEntity.java — verificar que updateFrom inclui:
public void updateFrom(final Address address) {
    this.label            = address.getLabel();
    this.street           = address.getStreet();
    // ... demais campos ...
    this.isDefault        = address.isDefault();   // ← OBRIGATÓRIO para setAsDefault funcionar
    this.updatedAt        = address.getUpdatedAt(); // ← OBRIGATÓRIO
    // NÃO tocar em: id, userId, createdAt
}
```

### 5. Roteamento e Injeção (API)

**`SetDefaultAddressResponse.java`**:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.SetDefaultAddressOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída para {@code PATCH /api/v1/users/me/addresses/{id}/default}.
 *
 * <p>Retorna o endereço completo após ser marcado como padrão,
 * com {@code is_default: true} confirmando a operação.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetDefaultAddressResponse(
        String id,
        @JsonProperty("user_id")            String userId,
        String label,
        @JsonProperty("recipient_name")     String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        @JsonProperty("postal_code")        String postalCode,
        String country,
        @JsonProperty("is_default")         boolean isDefault,
        @JsonProperty("is_billing_address") boolean isBillingAddress,
        @JsonProperty("created_at")         Instant createdAt,
        @JsonProperty("updated_at")         Instant updatedAt
) {
    public static SetDefaultAddressResponse from(final SetDefaultAddressOutput output) {
        return new SetDefaultAddressResponse(
                output.id(),
                output.userId(),
                output.label(),
                output.recipientName(),
                output.street(),
                output.number(),
                output.complement(),
                output.neighborhood(),
                output.city(),
                output.state(),
                output.postalCode(),
                output.country(),
                output.isDefault(),
                output.isBillingAddress(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}
```

**`AddressController.java`** — adicionar endpoint `PATCH /{id}/default` e atualizar construtor:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.AddAddressCommand;
import com.btree.application.usecase.user.address.AddAddressUseCase;
import com.btree.application.usecase.user.address.DeleteAddressCommand;
import com.btree.application.usecase.user.address.DeleteAddressUseCase;
import com.btree.application.usecase.user.address.SetDefaultAddressCommand;
import com.btree.application.usecase.user.address.SetDefaultAddressUseCase;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
 *   <li>UC-17 AddAddress       — {@code POST /}</li>
 *   <li>UC-18 UpdateAddress    — {@code PUT /{id}}</li>
 *   <li>UC-19 DeleteAddress    — {@code DELETE /{id}}</li>
 *   <li>UC-20 SetDefaultAddress — {@code PATCH /{id}/default}</li>
 *   <li>UC-21 ListAddresses    — {@code GET /} (próximo)</li>
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
    private final SetDefaultAddressUseCase setDefaultAddressUseCase;

    public AddressController(
            final AddAddressUseCase addAddressUseCase,
            final UpdateAddressUseCase updateAddressUseCase,
            final DeleteAddressUseCase deleteAddressUseCase,
            final SetDefaultAddressUseCase setDefaultAddressUseCase
    ) {
        this.addAddressUseCase        = addAddressUseCase;
        this.updateAddressUseCase     = updateAddressUseCase;
        this.deleteAddressUseCase     = deleteAddressUseCase;
        this.setDefaultAddressUseCase = setDefaultAddressUseCase;
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
            description = "Aplica soft delete em um endereço. " +
                          "Não é possível remover o endereço padrão se houver outros ativos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Endereço removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, já removido, " +
                                                             "não pertence ao usuário ou é o padrão com outros ativos")
    })
    public void delete(@PathVariable final String id) {
        final String userId = currentUserId();
        deleteAddressUseCase
                .execute(new DeleteAddressCommand(userId, id))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    // ── UC-20: SetDefaultAddress ──────────────────────────────────────────

    @PatchMapping("/{id}/default")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Definir endereço padrão",
            description = "Marca um endereço como padrão de entrega, removendo a marcação " +
                          "do endereço padrão anterior em operação atômica. " +
                          "Se o endereço já for o padrão, a operação é idempotente — retorna 200 sem modificações."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endereço definido como padrão com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, deletado ou não pertence ao usuário")
    })
    public SetDefaultAddressResponse setDefault(@PathVariable final String id) {
        final String userId = currentUserId();

        return SetDefaultAddressResponse.from(
                setDefaultAddressUseCase
                        .execute(new SetDefaultAddressCommand(userId, id))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
```

> **`@PatchMapping` para operação parcial:** `PATCH` é semanticamente correto para uma mutação parcial (apenas `isDefault`). `PUT` substituiria o recurso completo — incorreto aqui. O endpoint não recebe body, pois a semântica é clara: "marcar este endereço específico como padrão".

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public SetDefaultAddressUseCase setDefaultAddressUseCase(
        final AddressGateway addressGateway,
        final TransactionManager transactionManager
) {
    return new SetDefaultAddressUseCase(addressGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `UserError.USER_NOT_FOUND` | `userId` nulo, vazio ou UUID inválido no JWT | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_NOT_FOUND` | `addressId` nulo, UUID inválido ou endereço inexistente | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_ALREADY_DELETED` | Endereço com `deleted_at` preenchido | `422 Unprocessable Entity` |
| `AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER` | `address.userId != userId` do JWT | `422 Unprocessable Entity` |
| `AuthenticationException` | JWT ausente ou inválido | `401 Unauthorized` |

> **Sem erro para "já é padrão":** a idempotência é tratada como sucesso silencioso — retorna `200` com o endereço inalterado. Isso permite que o frontend chame este endpoint sem precisar verificar o estado atual, simplificando a lógica do cliente.

---

## 🌐 Contrato da API REST

### Request

```http
PATCH /api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678/default
Authorization: Bearer <access_token>
```

Sem body.

### Response (Sucesso — 200 OK)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "label": "Trabalho",
  "recipient_name": "João Silva",
  "street": "Av. Paulista",
  "number": "900",
  "complement": "Andar 15",
  "neighborhood": "Bela Vista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01311-000",
  "country": "BR",
  "is_default": true,
  "is_billing_address": false,
  "created_at": "2026-04-09T10:00:00Z",
  "updated_at": "2026-04-09T16:45:00Z"
}
```

> `"is_default": true` sempre no response de sucesso — confirma a operação ao cliente.

### Response (Idempotência — 200 OK, endereço já era padrão)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "label": "Trabalho",
  "street": "Av. Paulista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01311-000",
  "country": "BR",
  "is_default": true,
  "is_billing_address": false,
  "created_at": "2026-04-09T10:00:00Z",
  "updated_at": "2026-04-09T10:00:00Z"
}
```

> `updated_at` inalterado — nenhuma escrita ocorreu no banco.

### Response (Erro — 422 Endereço deletado)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço já foi removido",
  "errors": ["Endereço já foi removido"],
  "timestamp": "2026-04-09T16:45:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678/default"
}
```

### Response (Erro — 422 Endereço de outro usuário)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço não pertence ao usuário informado",
  "errors": ["Endereço não pertence ao usuário informado"],
  "timestamp": "2026-04-09T16:45:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678/default"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `AddressGateway`** — confirmar que `findById`, `update` e `clearDefaultByUserId` existem do UC-17.
2. **Verificar `AddressJpaEntity.updateFrom`** — confirmar que `isDefault` e `updatedAt` são transferidos.
3. **`SetDefaultAddressCommand.java`** — record com `userId` e `addressId`.
4. **`SetDefaultAddressOutput.java`** — record completo com factory `from(Address)`.
5. **`SetDefaultAddressUseCase.java`** — lógica com idempotência, verificação de posse e transação atômica.
6. **`UseCaseConfig.java`** — registrar `@Bean` do `SetDefaultAddressUseCase`.
7. **`SetDefaultAddressResponse.java`** — record com `@JsonInclude(NON_NULL)` e factory.
8. **`AddressController.java`** — adicionar `setDefaultAddressUseCase` ao construtor e endpoint `PATCH /{id}/default`.
9. **Testes unitários** — `SetDefaultAddressUseCaseTest` em `application/` com Mockito.
10. **Testes de integração** — `AddressPostgresGatewayIT` em `infrastructure/` cobrindo `clearDefaultByUserId` + `update` em sequência.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `SetDefaultAddressUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Endereço válido, não padrão, pertence ao usuário | `Right(output)` com `isDefault = true`, `clearDefaultByUserId` e `update` chamados |
| Endereço já é padrão (`isDefault = true`) | `Right(output)` idempotente, `clearDefaultByUserId` e `update` **não** chamados |
| `userId` nulo | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` em branco | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `addressId` nulo | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` em branco | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` UUID inválido | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| Endereço não existe no gateway | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| Endereço com `isDeleted() = true` | `Left(Notification)` com `AddressError.ADDRESS_ALREADY_DELETED` |
| Endereço pertence a outro usuário | `Left(Notification)` com `AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER` |
| `clearDefaultByUserId` chamado antes de `update` | Ordem verificada via `InOrder` do Mockito |
| Gateway `update()` lança exceção | `Left(Notification)` via `Try().toEither()` — `clearDefaultByUserId` revertido pela transação |

### Integração (`infrastructure/`) — `AddressPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| `clearDefaultByUserId` + `update(setAsDefault)` em sequência | Exatamente 1 endereço padrão após a operação |
| Usuário com endereço A padrão, define B como padrão | A: `is_default = false`, B: `is_default = true` |
| Usuário com nenhum endereço padrão, define um | Apenas o definido fica com `is_default = true` |
| `clearDefaultByUserId` com nenhum endereço padrão ativo | Retorna `0`, sem exceção |
| Endereço de outro usuário não afetado por `clearDefaultByUserId` | `is_default` do outro usuário permanece `true` |
| `updated_at` do endereço anterior ao padrão | Atualizado por `clearDefaultByUserId` via `@Modifying` |
| `updated_at` do novo endereço padrão | Atualizado por `update` com `setAsDefault()` |
| Idempotência no banco — `update` de endereço já padrão | Apenas 1 escrita (não chama gateway), banco inalterado |
| Soft-deletados ignorados por `clearDefaultByUserId` | Endereços com `deleted_at` não são afetados pela query |