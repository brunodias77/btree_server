# Task: UC-18 — UpdateAddress

## 📋 Resumo

Permite que o usuário autenticado edite os dados de um endereço de entrega já cadastrado em sua conta — logradouro, número, complemento, bairro, cidade, estado, CEP e rótulo. É uma funcionalidade essencial para que o usuário mantenha seus endereços atualizados sem precisar deletar e recriar, preservando histórico e referências em pedidos existentes.

## 🎯 Objetivo

Implementar o endpoint `PUT /api/v1/users/me/addresses/{id}` que recebe os campos editáveis de um endereço, valida que o endereço pertence ao usuário autenticado (segurança de posse), aplica as validações de domínio, persiste as alterações e retorna o endereço atualizado. O `userId` é extraído do JWT — o usuário só pode editar seus próprios endereços.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `PUT /api/v1/users/me/addresses/{id}`
- **Tabelas do Banco:** `users.addresses`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/entity/Address.java` — **alterar**: adicionar método de mutação `updateData(...)` para edição dos campos editáveis
2. `domain/src/main/java/com/btree/domain/user/error/AddressError.java` — **alterar**: adicionar `ADDRESS_BELONGS_TO_ANOTHER_USER`
3. `domain/src/main/java/com/btree/domain/user/gateway/AddressGateway.java` — **verificar**: confirmar que `findById`, `update` e `findByUserId` foram adicionados no UC-17

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/address/UpdateAddressCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/address/UpdateAddressOutput.java` — **criar**
3. `application/src/main/java/com/btree/application/usecase/user/address/UpdateAddressUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/AddressPostgresGateway.java` — **verificar**: confirmar que `findById` e `update` foram implementados no UC-17

### `api`

1. `api/src/main/java/com/btree/api/user/address/UpdateAddressRequest.java` — **criar**
2. `api/src/main/java/com/btree/api/user/address/UpdateAddressResponse.java` — **criar**
3. `api/src/main/java/com/btree/api/user/address/AddressController.java` — **alterar**: adicionar endpoint `PUT /{id}`
4. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)

**`AddressError.java`** — adicionar constante de segurança de posse:

```java
// AddressError.java — adicionar

public static final Error ADDRESS_BELONGS_TO_ANOTHER_USER =
    new Error("Endereço não pertence ao usuário informado");
```

**`Address.java`** — adicionar método de mutação `updateData`:

```java
// Address.java — adicionar método de mutação

/**
 * Atualiza os dados editáveis do endereço.
 *
 * <p>Campos imutáveis após criação: {@code id}, {@code userId}, {@code createdAt}.
 * O campo {@code isDefault} não é editável por este método — use
 * {@code setAsDefault()} / {@code unsetDefault()} (UC-20).
 *
 * <p>Após chamar este método, o caller deve invocar {@code validate(notification)}
 * para garantir que o estado resultante é válido antes de persistir.
 *
 * @param label            rótulo do endereço (ex: "Casa", "Trabalho")
 * @param recipientName    nome do destinatário
 * @param street           logradouro (obrigatório)
 * @param number           número
 * @param complement       complemento
 * @param neighborhood     bairro
 * @param city             cidade (obrigatório)
 * @param state            UF com 2 letras maiúsculas (obrigatório)
 * @param postalCode       CEP no formato XXXXX-XXX ou XXXXXXXX (obrigatório)
 * @param country          país ISO-2
 * @param isBillingAddress se é endereço de cobrança
 */
public void updateData(
        final String label,
        final String recipientName,
        final String street,
        final String number,
        final String complement,
        final String neighborhood,
        final String city,
        final String state,
        final String postalCode,
        final String country,
        final boolean isBillingAddress
) {
    this.label            = label;
    this.recipientName    = recipientName;
    this.street           = street;
    this.number           = number;
    this.complement       = complement;
    this.neighborhood     = neighborhood;
    this.city             = city;
    this.state            = state;
    this.postalCode       = postalCode;
    this.country          = country != null ? country : "BR";
    this.isBillingAddress = isBillingAddress;
    this.updatedAt        = java.time.Instant.now();
}
```

**`AddressGateway.java`** — verificar presença dos seguintes métodos (adicionados no UC-17):

```java
// Verificar que existem no AddressGateway:
Address save(Address address);
Address update(Address address);
Optional<Address> findById(AddressId id);
List<Address> findByUserId(UserId userId);
long countActiveByUserId(UserId userId);
void clearDefaultByUserId(UserId userId);
```

Nenhuma adição necessária para este use case — todos os métodos necessários já existem.

### 2. Contrato de Entrada/Saída (Application)

**`UpdateAddressCommand.java`**:

```java
package com.btree.application.usecase.user.address;

/**
 * Comando de entrada para UC-18 — UpdateAddress.
 *
 * @param userId          ID do usuário autenticado (extraído do JWT)
 * @param addressId       ID do endereço a editar
 * @param label           rótulo do endereço
 * @param recipientName   nome do destinatário
 * @param street          logradouro (obrigatório)
 * @param number          número
 * @param complement      complemento
 * @param neighborhood    bairro
 * @param city            cidade (obrigatório)
 * @param state           UF com 2 letras maiúsculas (obrigatório)
 * @param postalCode      CEP no formato XXXXX-XXX ou XXXXXXXX (obrigatório)
 * @param country         país ISO-2 (padrão: "BR")
 * @param isBillingAddress se é endereço de cobrança
 */
public record UpdateAddressCommand(
        String userId,
        String addressId,
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
        boolean isBillingAddress
) {}
```

**`UpdateAddressOutput.java`**:

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-18 — UpdateAddress.
 *
 * <p>Retorna o endereço completo após a atualização, incluindo
 * campos não editáveis por este use case ({@code isDefault})
 * para que o cliente possa manter seu estado local sincronizado.
 */
public record UpdateAddressOutput(
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
    public static UpdateAddressOutput from(final Address address) {
        return new UpdateAddressOutput(
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
 * Caso de uso UC-18 — UpdateAddress [CMD P0].
 *
 * <p>Edita os dados de um endereço de entrega existente do usuário autenticado.
 *
 * <p>Regras de segurança e negócio:
 * <ul>
 *   <li>O endereço deve existir e não estar soft-deletado.</li>
 *   <li>O endereço deve pertencer ao {@code userId} do JWT — usuário não pode
 *       editar endereços de outros usuários.</li>
 *   <li>Campos imutáveis: {@code isDefault} (gerenciado pelo UC-20).</li>
 * </ul>
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato de {@code userId} e {@code addressId}.</li>
 *   <li>Busca o endereço pelo {@code addressId}.</li>
 *   <li>Verifica existência e não-deleção do endereço.</li>
 *   <li>Verifica posse — {@code address.userId} deve ser igual ao {@code userId} do JWT.</li>
 *   <li>Chama {@code address.updateData(...)} para mutar o aggregate.</li>
 *   <li>Valida invariantes via {@code AddressValidator}.</li>
 *   <li>Persiste via {@code addressGateway.update()} dentro da transação.</li>
 *   <li>Retorna {@link UpdateAddressOutput} com o endereço atualizado.</li>
 * </ol>
 */
public class UpdateAddressUseCase implements UseCase<UpdateAddressCommand, UpdateAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public UpdateAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateAddressOutput> execute(final UpdateAddressCommand command) {
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

        // 6. Mutar o aggregate
        address.updateData(
                command.label(),
                command.recipientName(),
                command.street(),
                command.number(),
                command.complement(),
                command.neighborhood(),
                command.city(),
                command.state(),
                command.postalCode(),
                command.country(),
                command.isBillingAddress()
        );

        // 7. Validar invariantes após mutação
        address.validate(notification);
        if (notification.hasError()) {
            return Left(notification);
        }

        // 8. Persistir dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var updated = addressGateway.update(address);
            return UpdateAddressOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Por que verificar posse antes de mutar?** A verificação `address.getUserId() != userId` é feita antes de qualquer mutação. Isso garante que nenhuma operação parcial ocorra em caso de tentativa de acesso indevido — princípio de fail-fast em segurança.

### 4. Persistência (Infrastructure)

Nenhum arquivo novo de persistência é necessário — todos os métodos utilizados (`findById` e `update`) foram implementados no UC-17 em `AddressPostgresGateway` e `AddressJpaRepository`.

Verificar que `AddressPostgresGateway.update()` usa `updateFrom` e não recria a entity:

```java
// AddressPostgresGateway.java — verificar implementação existente do UC-17

@Override
public Address update(final Address address) {
    final var entity = addressJpaRepository.findById(address.getId().getValue())
            .orElseThrow(() -> NotFoundException.with(
                    "Endereço não encontrado: " + address.getId().getValue()));
    entity.updateFrom(address);           // ← preserva id, userId, createdAt
    return addressJpaRepository.save(entity).toAggregate();
}
```

E que `AddressJpaEntity.updateFrom` **não** sobrescreve campos imutáveis:

```java
// AddressJpaEntity.java — verificar que updateFrom NÃO toca em:
// - id
// - userId
// - createdAt
// Esses campos são imutáveis e preservados pelo Hibernate via @Column(updatable = false)
```

### 5. Roteamento e Injeção (API)

**`UpdateAddressRequest.java`**:

```java
package com.btree.api.user.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO HTTP de entrada para {@code PUT /api/v1/users/me/addresses/{id}}.
 *
 * <p>Payload completo obrigatório — campos ausentes são gravados como {@code null}.
 * O cliente deve enviar todos os valores desejados, não apenas os alterados.
 */
public record UpdateAddressRequest(

        @Size(max = 50, message = "label deve ter no máximo 50 caracteres")
        String label,

        @Size(max = 150, message = "recipientName deve ter no máximo 150 caracteres")
        @JsonProperty("recipient_name")
        String recipientName,

        @NotBlank(message = "street é obrigatório")
        @Size(max = 255, message = "street deve ter no máximo 255 caracteres")
        String street,

        @Size(max = 20, message = "number deve ter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "complement deve ter no máximo 100 caracteres")
        String complement,

        @Size(max = 100, message = "neighborhood deve ter no máximo 100 caracteres")
        String neighborhood,

        @NotBlank(message = "city é obrigatório")
        @Size(max = 100, message = "city deve ter no máximo 100 caracteres")
        String city,

        @NotBlank(message = "state é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "state deve conter exatamente 2 letras maiúsculas")
        String state,

        @NotBlank(message = "postalCode é obrigatório")
        @Pattern(
            regexp = "^\\d{5}-?\\d{3}$",
            message = "postalCode deve estar no formato XXXXX-XXX ou XXXXXXXX"
        )
        @JsonProperty("postal_code")
        String postalCode,

        @Size(min = 2, max = 2, message = "country deve ter exatamente 2 caracteres")
        String country,

        @JsonProperty("is_billing_address")
        boolean isBillingAddress
) {}
```

**`UpdateAddressResponse.java`**:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.UpdateAddressOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída para {@code PUT /api/v1/users/me/addresses/{id}}.
 *
 * <p>Inclui {@code updatedAt} para que o cliente saiba o timestamp exato
 * da última modificação — útil para cache e exibição de auditoria.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateAddressResponse(
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
    public static UpdateAddressResponse from(final UpdateAddressOutput output) {
        return new UpdateAddressResponse(
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

**`AddressController.java`** — adicionar endpoint `PUT /{id}` ao controller existente:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.AddAddressCommand;
import com.btree.application.usecase.user.address.AddAddressUseCase;
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
 *   <li>UC-19 DeleteAddress — {@code DELETE /{id}} (próximo)</li>
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

    public AddressController(
            final AddAddressUseCase addAddressUseCase,
            final UpdateAddressUseCase updateAddressUseCase
    ) {
        this.addAddressUseCase = addAddressUseCase;
        this.updateAddressUseCase = updateAddressUseCase;
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
            description = "Atualiza os dados de um endereço existente do usuário autenticado. " +
                          "Enviar payload completo — campos ausentes são gravados como null. " +
                          "O campo isDefault não é alterável por este endpoint (use PATCH /{id}/default)."
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
```

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public UpdateAddressUseCase updateAddressUseCase(
        final AddressGateway addressGateway,
        final TransactionManager transactionManager
) {
    return new UpdateAddressUseCase(addressGateway, transactionManager);
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
| `AddressError.STREET_EMPTY` | `street` nulo ou em branco | `400 Bad Request` (Bean Validation) |
| `AddressError.CITY_EMPTY` | `city` nulo ou em branco | `400 Bad Request` (Bean Validation) |
| `AddressError.STATE_INVALID` | `state` não corresponde a 2 letras maiúsculas | `400 Bad Request` (Bean Validation) |
| `AddressError.POSTAL_CODE_INVALID` | CEP fora do formato aceito | `400 Bad Request` (Bean Validation) |
| `AuthenticationException` | JWT ausente ou inválido | `401 Unauthorized` |

> **Nota de segurança:** `ADDRESS_BELONGS_TO_ANOTHER_USER` retorna `422` — não `403`. Isso evita que um atacante descubra quais IDs de endereço existem no sistema. A resposta é idêntica a "endereço não encontrado" do ponto de vista do protocolo HTTP, mas com mensagem distinta para fins de log interno.

---

## 🌐 Contrato da API REST

### Request

```http
PUT /api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "label": "Casa Nova",
  "recipient_name": "João Silva",
  "street": "Av. Brigadeiro Faria Lima",
  "number": "3477",
  "complement": "Andar 10",
  "neighborhood": "Itaim Bibi",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "04538-133",
  "country": "BR",
  "is_billing_address": true
}
```

### Response (Sucesso — 200 OK)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "label": "Casa Nova",
  "recipient_name": "João Silva",
  "street": "Av. Brigadeiro Faria Lima",
  "number": "3477",
  "complement": "Andar 10",
  "neighborhood": "Itaim Bibi",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "04538-133",
  "country": "BR",
  "is_default": true,
  "is_billing_address": true,
  "created_at": "2026-04-09T14:00:00Z",
  "updated_at": "2026-04-09T15:32:00Z"
}
```

### Response (Erro — 422 Endereço não pertence ao usuário)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço não pertence ao usuário informado",
  "errors": ["Endereço não pertence ao usuário informado"],
  "timestamp": "2026-04-09T15:32:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

### Response (Erro — 400 Bean Validation)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "street: street é obrigatório",
  "errors": [
    "street: street é obrigatório",
    "state: state deve conter exatamente 2 letras maiúsculas"
  ],
  "timestamp": "2026-04-09T15:32:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

### Response (Erro — 422 Endereço deletado)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Endereço já foi removido",
  "errors": ["Endereço já foi removido"],
  "timestamp": "2026-04-09T15:32:00Z",
  "path": "/api/v1/users/me/addresses/019486ab-c123-7def-a456-789012345678"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`AddressError.java`** — adicionar `ADDRESS_BELONGS_TO_ANOTHER_USER`.
2. **`Address.java`** — adicionar método de mutação `updateData(...)`.
3. **`UpdateAddressCommand.java`** — record com `userId`, `addressId` e todos os campos editáveis.
4. **`UpdateAddressOutput.java`** — record com factory `from(Address)` incluindo `updatedAt`.
5. **`UpdateAddressUseCase.java`** — lógica com verificação de posse e `Either`.
6. **`UseCaseConfig.java`** — registrar `@Bean` do `UpdateAddressUseCase`.
7. **`UpdateAddressRequest.java`** — record com Bean Validation (idêntico ao `AddAddressRequest`).
8. **`UpdateAddressResponse.java`** — record com `updatedAt` e factory `from(UpdateAddressOutput)`.
9. **`AddressController.java`** — adicionar construtor atualizado com ambos os use cases e endpoint `PUT /{id}`.
10. **Testes unitários** — `UpdateAddressUseCaseTest` em `application/` com Mockito.
11. **Testes de integração** — `AddressPostgresGatewayIT` em `infrastructure/` cobrindo `update`.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `UpdateAddressUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Payload válido — endereço pertence ao usuário | `Right(UpdateAddressOutput)` com todos os campos atualizados |
| `userId` nulo | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `addressId` nulo | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` UUID inválido | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| `addressId` não existe no gateway | `Left(Notification)` com `AddressError.ADDRESS_NOT_FOUND` |
| Endereço com `isDeleted() = true` | `Left(Notification)` com `AddressError.ADDRESS_ALREADY_DELETED` |
| Endereço pertence a outro usuário | `Left(Notification)` com `AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER` |
| `street` vazio após mutação | `Left(Notification)` com `AddressError.STREET_EMPTY` |
| `state` inválido após mutação | `Left(Notification)` com `AddressError.STATE_INVALID` |
| `postalCode` inválido após mutação | `Left(Notification)` com `AddressError.POSTAL_CODE_INVALID` |
| `isDefault` preservado após update | Output retorna `isDefault` original — não alterado por este use case |
| `country` nulo → padrão `"BR"` | `address.getCountry()` retorna `"BR"` |
| Gateway `update()` lança exceção | `Left(Notification)` via `Try().toEither()` |
| Múltiplos erros de validação simultâneos | Todos acumulados no `Notification` |

### Integração (`infrastructure/`) — `AddressPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| `update()` altera `street` | Novo valor persistido, `id` e `userId` inalterados |
| `update()` atualiza `updated_at` | `updatedAt` maior que `createdAt` após update |
| `update()` não altera `created_at` | Campo imutável preservado |
| `update()` não altera `is_default` | `isDefault` permanece como estava antes |
| `update()` com `isBillingAddress = true` | Campo persistido corretamente |
| `update()` com `label = null` | Campo armazenado como `null` |
| `update()` em endereço soft-deletado | `NotFoundException` lançada pelo gateway |
| `findById()` retorna endereço após update | Dados atualizados visíveis na leitura subsequente |