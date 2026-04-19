# Task: UC-149 — UpdateCoupon

## 📋 Resumo

Permite que administradores editem os valores e a validade de um cupom existente. Campos imutáveis (como `code`, `coupon_type` e `coupon_scope`) permanecem inalterados após a criação. A operação utiliza optimistic locking para evitar escritas concorrentes e registra um `CouponUpdatedEvent` no outbox.

## 🎯 Objetivo

Implementar o use case `UpdateCouponUseCase` que atualiza os campos mutáveis de um cupom (`description`, `discount_value`, `min_order_value`, `max_discount_amount`, `max_uses`, `max_uses_per_user`, `starts_at`, `expires_at` e listas de elegibilidade), retornando o aggregate atualizado como `UpdateCouponOutput`.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `ALTA`
- **Endpoint:** `PUT /api/v1/coupons/{id}`
- **Tabelas do Banco:** `coupons.coupons`, `coupons.eligible_categories`, `coupons.eligible_products`, `coupons.eligible_brands`, `coupons.eligible_users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. **ALTERAR** `domain/src/main/java/com/btree/domain/coupon/entity/Coupon.java`
   — Adicionar método de mutação `update(...)` que aplica os novos valores e registra `CouponUpdatedEvent`.
2. **ALTERAR** `domain/src/main/java/com/btree/domain/coupon/error/CouponError.java`
   — Adicionar constantes `COUPON_NOT_FOUND`, `COUPON_DELETED`, `COUPON_NOT_EDITABLE`, `DISCOUNT_VALUE_INVALIDO`, `EXPIRES_AT_INVALIDO`, `MAX_USES_BELOW_CURRENT`, `MAX_USES_PER_USER_INVALIDO`.
3. **ALTERAR** `domain/src/main/java/com/btree/domain/coupon/gateway/CouponGateway.java`
   — Garantir assinatura `Coupon update(Coupon coupon)` (adicionar se ausente).
4. **CRIAR** `domain/src/main/java/com/btree/domain/coupon/events/CouponUpdatedEvent.java`
   — Domain Event registrado pelo aggregate ao ser atualizado.
5. **ALTERAR** `domain/src/main/java/com/btree/domain/coupon/validator/CouponValidator.java`
   — Garantir cobertura das regras de `discount_value`, datas e listas de elegibilidade usadas no update.

### `application`

1. **CRIAR** `application/src/main/java/com/btree/application/coupon/update/UpdateCouponUseCase.java`
2. **CRIAR** `application/src/main/java/com/btree/application/coupon/update/UpdateCouponCommand.java`
3. **CRIAR** `application/src/main/java/com/btree/application/coupon/update/UpdateCouponOutput.java`

### `infrastructure`

1. **ALTERAR** `infrastructure/src/main/java/com/btree/infrastructure/coupon/entity/CouponJpaEntity.java`
   — Garantir método `updateFrom(Coupon aggregate)` que atualiza apenas campos mutáveis (nunca sobrescreve `id`, `version`, `code`, `coupon_type`, `coupon_scope`, `created_at`). Garantir que as coleções de elegibilidade (`eligibleCategories`, `eligibleProducts`, `eligibleBrands`, `eligibleUsers`) são limpadas e recarregadas no `updateFrom`.
2. **ALTERAR** `infrastructure/src/main/java/com/btree/infrastructure/coupon/persistence/CouponPostgresGateway.java`
   — Garantir implementação de `update(Coupon coupon)` usando `findById` + `updateFrom` + `save`.

### `api`

1. **CRIAR** `api/src/main/java/com/btree/api/coupon/UpdateCouponRequest.java`
2. **CRIAR** `api/src/main/java/com/btree/api/coupon/UpdateCouponResponse.java`
3. **ALTERAR** `api/src/main/java/com/btree/api/coupon/CouponController.java`
   — Adicionar endpoint `PUT /{id}`.
4. **ALTERAR** `api/src/main/java/com/btree/api/config/UseCaseConfig.java`
   — Registrar `@Bean` para `UpdateCouponUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)

#### `Coupon.java` — método de mutação `update`

O aggregate já deve possuir os factory methods `create(...)` e `with(...)`. Adicionar:

```java
public void update(
        final String description,
        final BigDecimal discountValue,
        final BigDecimal minOrderValue,
        final BigDecimal maxDiscountAmount,
        final Integer maxUses,
        final int maxUsesPerUser,
        final Instant startsAt,
        final Instant expiresAt,
        final List<UUID> eligibleCategoryIds,
        final List<UUID> eligibleProductIds,
        final List<UUID> eligibleBrandIds,
        final List<UUID> eligibleUserIds,
        final Notification notification
) {
    this.description     = description;
    this.discountValue   = discountValue;
    this.minOrderValue   = minOrderValue;
    this.maxDiscountAmount = maxDiscountAmount;
    this.maxUses         = maxUses;
    this.maxUsesPerUser  = maxUsesPerUser;
    this.startsAt        = startsAt;
    this.expiresAt       = expiresAt;
    this.eligibleCategoryIds = eligibleCategoryIds != null ? eligibleCategoryIds : List.of();
    this.eligibleProductIds  = eligibleProductIds  != null ? eligibleProductIds  : List.of();
    this.eligibleBrandIds    = eligibleBrandIds    != null ? eligibleBrandIds    : List.of();
    this.eligibleUserIds     = eligibleUserIds     != null ? eligibleUserIds     : List.of();
    this.updatedAt = Instant.now();
    this.validate(notification);
    if (!notification.hasError()) {
        registerEvent(new CouponUpdatedEvent(
                this.id.getValue().toString(),
                this.code,
                this.couponType,
                this.discountValue
        ));
    }
}
```

#### `CouponError.java` — constantes a adicionar

```java
public static final Error COUPON_NOT_FOUND        = new Error("Cupom não encontrado");
public static final Error COUPON_DELETED           = new Error("Cupom excluído não pode ser editado");
public static final Error COUPON_NOT_EDITABLE      = new Error("Cupom no status '%s' não pode ser editado");
public static final Error DISCOUNT_VALUE_INVALIDO  = new Error("'discount_value' deve ser maior que zero");
public static final Error EXPIRES_AT_INVALIDO      = new Error("'expires_at' deve ser posterior a 'starts_at'");
public static final Error MAX_USES_BELOW_CURRENT   = new Error("'max_uses' não pode ser menor que o total de usos já registrados (%d)");
public static final Error MAX_USES_PER_USER_INVALIDO = new Error("'max_uses_per_user' deve ser maior ou igual a 1");
```

#### `CouponUpdatedEvent.java`

```java
package com.btree.domain.coupon.events;

import com.btree.shared.domain.DomainEvent;
import com.btree.shared.enums.CouponType;
import java.math.BigDecimal;

public class CouponUpdatedEvent extends DomainEvent {

    private final String couponId;
    private final String couponCode;
    private final CouponType couponType;
    private final BigDecimal discountValue;

    public CouponUpdatedEvent(
            final String couponId,
            final String couponCode,
            final CouponType couponType,
            final BigDecimal discountValue
    ) {
        super();
        this.couponId      = couponId;
        this.couponCode    = couponCode;
        this.couponType    = couponType;
        this.discountValue = discountValue;
    }

    @Override public String getAggregateId()   { return couponId; }
    @Override public String getAggregateType() { return "Coupon"; }
    @Override public String getEventType()     { return "coupon.updated"; }

    public String getCouponCode()      { return couponCode; }
    public CouponType getCouponType()  { return couponType; }
    public BigDecimal getDiscountValue() { return discountValue; }
}
```

#### `CouponGateway.java` — assinatura mínima esperada

```java
package com.btree.domain.coupon.gateway;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.identifier.CouponId;
import java.util.Optional;

public interface CouponGateway {
    Coupon save(Coupon coupon);
    Coupon update(Coupon coupon);
    Optional<Coupon> findById(CouponId id);
    boolean existsByCode(String code);
    // demais métodos já existentes...
}
```

#### `CouponValidator.java` — regras relevantes para update

```java
private void checkDiscountValue() {
    if (coupon.getDiscountValue() == null || coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
        this.validationHandler().append(CouponError.DISCOUNT_VALUE_INVALIDO);
    }
}

private void checkDates() {
    if (coupon.getStartsAt() == null) {
        this.validationHandler().append(new Error("'starts_at' não pode ser nulo"));
        return;
    }
    if (coupon.getExpiresAt() != null && !coupon.getExpiresAt().isAfter(coupon.getStartsAt())) {
        this.validationHandler().append(CouponError.EXPIRES_AT_INVALIDO);
    }
}

private void checkMaxUsesPerUser() {
    if (coupon.getMaxUsesPerUser() < 1) {
        this.validationHandler().append(CouponError.MAX_USES_PER_USER_INVALIDO);
    }
}
```

---

### 2. Contrato de Entrada/Saída (Application)

#### `UpdateCouponCommand.java`

```java
package com.btree.application.coupon.update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponCommand(
        UUID id,
        String description,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Integer maxUses,
        int maxUsesPerUser,
        Instant startsAt,
        Instant expiresAt,
        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds
) {}
```

#### `UpdateCouponOutput.java`

```java
package com.btree.application.coupon.update;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponOutput(
        UUID id,
        String code,
        String description,
        CouponType couponType,
        CouponScope couponScope,
        CouponStatus status,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Integer maxUses,
        int maxUsesPerUser,
        int currentUses,
        Instant startsAt,
        Instant expiresAt,
        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds,
        Instant updatedAt,
        int version
) {
    public static UpdateCouponOutput from(final Coupon coupon) {
        return new UpdateCouponOutput(
                coupon.getId().getValue(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getCouponType(),
                coupon.getCouponScope(),
                coupon.getStatus(),
                coupon.getDiscountValue(),
                coupon.getMinOrderValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMaxUses(),
                coupon.getMaxUsesPerUser(),
                coupon.getCurrentUses(),
                coupon.getStartsAt(),
                coupon.getExpiresAt(),
                coupon.getEligibleCategoryIds(),
                coupon.getEligibleProductIds(),
                coupon.getEligibleBrandIds(),
                coupon.getEligibleUserIds(),
                coupon.getUpdatedAt(),
                coupon.getVersion()
        );
    }
}
```

---

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.coupon.update;

import com.btree.domain.coupon.entity.Coupon;
import com.btree.domain.coupon.error.CouponError;
import com.btree.domain.coupon.gateway.CouponGateway;
import com.btree.domain.coupon.identifier.CouponId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.util.Set;

import static io.vavr.control.Either.left;

public class UpdateCouponUseCase implements UseCase<UpdateCouponCommand, UpdateCouponOutput> {

    // Estados que permitem edição
    private static final Set<CouponStatus> EDITABLE_STATUSES = Set.of(
            CouponStatus.DRAFT,
            CouponStatus.ACTIVE,
            CouponStatus.INACTIVE,
            CouponStatus.PAUSED
    );

    private final CouponGateway couponGateway;
    private final TransactionManager transactionManager;

    public UpdateCouponUseCase(
            final CouponGateway couponGateway,
            final TransactionManager transactionManager
    ) {
        this.couponGateway      = couponGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateCouponOutput> execute(final UpdateCouponCommand command) {
        final var notification = Notification.create();

        // 1. Buscar o aggregate — lança NotFoundException (404) se não existir
        final var coupon = couponGateway.findById(CouponId.from(command.id()))
                .orElseThrow(() -> NotFoundException.with(Coupon.class, command.id()));

        // 2. Validações de negócio

        // 2a. Soft-delete: deleted_at != null (o gateway filtra, mas por defensividade)
        if (coupon.isDeleted()) {
            notification.append(CouponError.COUPON_DELETED);
            return left(notification);
        }

        // 2b. Status editável
        if (!EDITABLE_STATUSES.contains(coupon.getStatus())) {
            notification.append(new com.btree.shared.validation.Error(
                    String.format(CouponError.COUPON_NOT_EDITABLE.message(), coupon.getStatus().name())
            ));
            return left(notification);
        }

        // 2c. max_uses não pode ser reduzido abaixo de current_uses
        if (command.maxUses() != null && command.maxUses() < coupon.getCurrentUses()) {
            notification.append(new com.btree.shared.validation.Error(
                    String.format(CouponError.MAX_USES_BELOW_CURRENT.message(), coupon.getCurrentUses())
            ));
        }

        if (notification.hasError()) {
            return left(notification);
        }

        // 3. Aplicar mutação no aggregate (validações estruturais dentro do update)
        coupon.update(
                command.description(),
                command.discountValue(),
                command.minOrderValue(),
                command.maxDiscountAmount(),
                command.maxUses(),
                command.maxUsesPerUser(),
                command.startsAt(),
                command.expiresAt(),
                command.eligibleCategoryIds(),
                command.eligibleProductIds(),
                command.eligibleBrandIds(),
                command.eligibleUserIds(),
                notification
        );

        if (notification.hasError()) {
            return left(notification);
        }

        // 4. Persistir dentro da transação
        return Try.of(() -> transactionManager.execute(() -> {
            final var updated = couponGateway.update(coupon);
            return UpdateCouponOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Optimistic locking:** Se dois requests concorrentes tentarem atualizar o mesmo cupom, o Hibernate lança `ObjectOptimisticLockingFailureException`, que o `GlobalExceptionHandler` mapeia para `409 Conflict`.

---

### 4. Persistência (Infrastructure)

#### `CouponJpaEntity.java` — método `updateFrom`

```java
public void updateFrom(final Coupon aggregate) {
    // Campos mutáveis
    this.description       = aggregate.getDescription();
    this.discountValue     = aggregate.getDiscountValue();
    this.minOrderValue     = aggregate.getMinOrderValue();
    this.maxDiscountAmount = aggregate.getMaxDiscountAmount();
    this.maxUses           = aggregate.getMaxUses();
    this.maxUsesPerUser    = aggregate.getMaxUsesPerUser();
    this.startsAt          = aggregate.getStartsAt();
    this.expiresAt         = aggregate.getExpiresAt();
    this.updatedAt         = aggregate.getUpdatedAt();

    // Coleções de elegibilidade: limpar e recarregar
    // (orphanRemoval=true nas coleções garante remoção automática)
    this.eligibleCategories.clear();
    aggregate.getEligibleCategoryIds().forEach(catId ->
            this.eligibleCategories.add(new EligibleCategoryJpaEntity(this, catId)));

    this.eligibleProducts.clear();
    aggregate.getEligibleProductIds().forEach(prodId ->
            this.eligibleProducts.add(new EligibleProductJpaEntity(this, prodId)));

    this.eligibleBrands.clear();
    aggregate.getEligibleBrandIds().forEach(brandId ->
            this.eligibleBrands.add(new EligibleBrandJpaEntity(this, brandId)));

    this.eligibleUsers.clear();
    aggregate.getEligibleUserIds().forEach(userId ->
            this.eligibleUsers.add(new EligibleUserJpaEntity(this, userId)));

    // NUNCA sobrescrever: id, code, coupon_type, coupon_scope, status,
    //                     current_uses, created_at, deleted_at, version
}
```

> As coleções `eligibleCategories`, `eligibleProducts`, `eligibleBrands` e `eligibleUsers` devem ter `orphanRemoval = true` e `cascade = {PERSIST, MERGE}` para que o JPA remova os registros órfãos automaticamente ao salvar.

#### `CouponPostgresGateway.java` — método `update`

```java
@Override
public Coupon update(final Coupon coupon) {
    final var entity = couponJpaRepository
            .findById(coupon.getId().getValue())
            .orElseThrow(() -> NotFoundException.with(Coupon.class, coupon.getId().getValue()));
    entity.updateFrom(coupon);
    return couponJpaRepository.save(entity).toAggregate();
}
```

---

### 5. Roteamento e Injeção (API)

#### `UpdateCouponRequest.java`

```java
package com.btree.api.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponRequest(
        String description,

        @NotNull
        @DecimalMin(value = "0.01", message = "'discount_value' deve ser maior que zero")
        BigDecimal discountValue,

        @DecimalMin(value = "0.00", inclusive = true, message = "'min_order_value' deve ser maior ou igual a zero")
        BigDecimal minOrderValue,

        @DecimalMin(value = "0.01", message = "'max_discount_amount' deve ser maior que zero")
        BigDecimal maxDiscountAmount,

        @Min(value = 1, message = "'max_uses' deve ser maior ou igual a 1")
        Integer maxUses,

        @NotNull
        @Min(value = 1, message = "'max_uses_per_user' deve ser maior ou igual a 1")
        Integer maxUsesPerUser,

        @NotNull
        Instant startsAt,

        Instant expiresAt,

        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds
) {}
```

#### `UpdateCouponResponse.java`

```java
package com.btree.api.coupon;

import com.btree.application.coupon.update.UpdateCouponOutput;
import com.btree.shared.enums.CouponScope;
import com.btree.shared.enums.CouponStatus;
import com.btree.shared.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateCouponResponse(
        UUID id,
        String code,
        String description,
        CouponType couponType,
        CouponScope couponScope,
        CouponStatus status,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Integer maxUses,
        int maxUsesPerUser,
        int currentUses,
        Instant startsAt,
        Instant expiresAt,
        List<UUID> eligibleCategoryIds,
        List<UUID> eligibleProductIds,
        List<UUID> eligibleBrandIds,
        List<UUID> eligibleUserIds,
        Instant updatedAt,
        int version
) {
    public static UpdateCouponResponse from(final UpdateCouponOutput output) {
        return new UpdateCouponResponse(
                output.id(),
                output.code(),
                output.description(),
                output.couponType(),
                output.couponScope(),
                output.status(),
                output.discountValue(),
                output.minOrderValue(),
                output.maxDiscountAmount(),
                output.maxUses(),
                output.maxUsesPerUser(),
                output.currentUses(),
                output.startsAt(),
                output.expiresAt(),
                output.eligibleCategoryIds(),
                output.eligibleProductIds(),
                output.eligibleBrandIds(),
                output.eligibleUserIds(),
                output.updatedAt(),
                output.version()
        );
    }
}
```

#### `CouponController.java` — endpoint PUT

```java
@PutMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(summary = "Atualizar cupom", description = "Edita valores e validade de um cupom existente.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cupom atualizado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "404", description = "Cupom não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (edição concorrente)"),
        @ApiResponse(responseCode = "422", description = "Regras de negócio violadas")
})
public UpdateCouponResponse update(
        @PathVariable final UUID id,
        @Valid @RequestBody final UpdateCouponRequest request
) {
    final var command = new UpdateCouponCommand(
            id,
            request.description(),
            request.discountValue(),
            request.minOrderValue(),
            request.maxDiscountAmount(),
            request.maxUses(),
            request.maxUsesPerUser(),
            request.startsAt(),
            request.expiresAt(),
            request.eligibleCategoryIds(),
            request.eligibleProductIds(),
            request.eligibleBrandIds(),
            request.eligibleUserIds()
    );
    return UpdateCouponResponse.from(
            updateCouponUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

#### `UseCaseConfig.java` — bean a adicionar

```java
@Bean
public UpdateCouponUseCase updateCouponUseCase(
        final CouponGateway couponGateway,
        final TransactionManager transactionManager
) {
    return new UpdateCouponUseCase(couponGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio                        | Condição                                                   | Status HTTP Resultante     |
| -------------------------------------- | ---------------------------------------------------------- | -------------------------- |
| `CouponError.COUPON_NOT_FOUND`         | `findById` retorna `Optional.empty()`                      | `404 Not Found`            |
| `CouponError.COUPON_DELETED`           | `coupon.isDeleted() == true`                               | `422 Unprocessable Entity` |
| `CouponError.COUPON_NOT_EDITABLE`      | Status não pertence a `{DRAFT, ACTIVE, INACTIVE, PAUSED}`  | `422 Unprocessable Entity` |
| `CouponError.MAX_USES_BELOW_CURRENT`   | `command.maxUses() < coupon.getCurrentUses()`              | `422 Unprocessable Entity` |
| `CouponError.DISCOUNT_VALUE_INVALIDO`  | `discountValue <= 0` ou nulo                               | `422 Unprocessable Entity` |
| `CouponError.EXPIRES_AT_INVALIDO`      | `expiresAt` presente e não posterior a `startsAt`          | `422 Unprocessable Entity` |
| `CouponError.MAX_USES_PER_USER_INVALIDO` | `maxUsesPerUser < 1`                                     | `422 Unprocessable Entity` |
| `ObjectOptimisticLockingFailureException` | Dois requests concorrentes sobre o mesmo cupom           | `409 Conflict`             |

---

## 🌐 Contrato da API REST

### Request — `PUT /api/v1/coupons/{id}`

```json
{
  "description": "10% de desconto em toda a loja",
  "discountValue": 10.00,
  "minOrderValue": 50.00,
  "maxDiscountAmount": 100.00,
  "maxUses": 500,
  "maxUsesPerUser": 1,
  "startsAt": "2026-05-01T00:00:00Z",
  "expiresAt": "2026-05-31T23:59:59Z",
  "eligibleCategoryIds": [],
  "eligibleProductIds": [],
  "eligibleBrandIds": [],
  "eligibleUserIds": []
}
```

### Response (Sucesso — 200)

```json
{
  "id": "019600ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx",
  "code": "SUMMER10",
  "description": "10% de desconto em toda a loja",
  "couponType": "PERCENTAGE",
  "couponScope": "ALL",
  "status": "ACTIVE",
  "discountValue": 10.00,
  "minOrderValue": 50.00,
  "maxDiscountAmount": 100.00,
  "maxUses": 500,
  "maxUsesPerUser": 1,
  "currentUses": 42,
  "startsAt": "2026-05-01T00:00:00Z",
  "expiresAt": "2026-05-31T23:59:59Z",
  "eligibleCategoryIds": [],
  "eligibleProductIds": [],
  "eligibleBrandIds": [],
  "eligibleUserIds": [],
  "updatedAt": "2026-04-16T14:30:00Z",
  "version": 3
}
```

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": [
    "'discount_value' deve ser maior que zero",
    "'expires_at' deve ser posterior a 'starts_at'"
  ],
  "timestamp": "2026-04-16T14:30:00Z",
  "path": "/api/v1/coupons/019600ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx"
}
```

### Response (Erro — 404)

```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Coupon com id '019600ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx' não foi encontrado"],
  "timestamp": "2026-04-16T14:30:00Z",
  "path": "/api/v1/coupons/019600ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx"
}
```

### Response (Erro — 409 — conflito de versão)

```json
{
  "status": 409,
  "error": "Conflict",
  "errors": ["O recurso foi modificado por outra requisição. Tente novamente."],
  "timestamp": "2026-04-16T14:30:00Z",
  "path": "/api/v1/coupons/019600ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> A migration Flyway (`V009`) já existe — **não criar nova migration**.

1. **`CouponError.java`** — adicionar as constantes de erro listadas acima.
2. **`CouponUpdatedEvent.java`** — criar domain event.
3. **`Coupon.java`** — adicionar método `update(...)` com chamada a `validate()` e `registerEvent()`.
4. **`CouponValidator.java`** — garantir cobertura das regras de discount_value, datas e maxUsesPerUser.
5. **`CouponGateway.java`** — garantir assinatura `Coupon update(Coupon coupon)`.
6. **`UpdateCouponCommand.java`** — record de entrada.
7. **`UpdateCouponOutput.java`** — record de saída com factory `from(Coupon)`.
8. **`UpdateCouponUseCase.java`** — lógica com `Either`, validações de negócio e transação.
9. **`CouponJpaEntity.java`** — adicionar/ajustar `updateFrom(Coupon)` com limpeza das coleções.
10. **`CouponPostgresGateway.java`** — implementar `update(Coupon)`.
11. **`UseCaseConfig.java`** — registrar `@Bean` para `UpdateCouponUseCase`.
12. **`UpdateCouponRequest.java`** e **`UpdateCouponResponse.java`** — records HTTP.
13. **`CouponController.java`** — adicionar endpoint `PUT /{id}`.
14. **Testes unitários** (`domain/` e `application/`) — JUnit 5 + Mockito, sem Spring:
    - `UpdateCouponUseCaseTest` — cobrir: sucesso, cupom não encontrado, cupom deletado, status não editável, max_uses abaixo do current_uses, discount_value inválido, expires_at inválido, conflito de versão (mock lança `ObjectOptimisticLockingFailureException`).
    - `CouponValidatorTest` — cobrir as regras de update.
15. **Testes de integração** (`*IT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `CouponPostgresGatewayIT` — cobrir `update()`: sucesso, optimistic locking (dois saves concorrentes), limpeza de elegibilidade.
