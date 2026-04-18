# Task: UC-71 — ReserveStock

## 📋 Resumo

Reservar estoque para um produto durante o processo de checkout, utilizando `SELECT FOR UPDATE` para garantir atomicidade e prevenir venda a maior em cenários de alta concorrência. A reserva é temporária (TTL configurável, padrão 15 minutos) e deve ser confirmada (`ConfirmStockDeduction` — UC-73) após o pagamento ou liberada (`ReleaseStock` — UC-72) em caso de cancelamento ou expiração.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/catalog/products/{productId}/stock/reservations` deve:

1. Adquirir lock pessimista (`SELECT FOR UPDATE`) na linha do produto.
2. Verificar que o estoque disponível líquido (= `quantity` − reservas ativas) cobre a quantidade solicitada.
3. Criar a `StockReservation` e gravar o `StockMovement` do tipo `RESERVE`.
4. Decrementar `catalog.products.quantity` pelo valor reservado.
5. Publicar `StockReservedEvent` via Outbox.
6. Retornar `201 Created` com os dados da reserva.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/products/{productId}/stock/reservations`
* **Tabelas do Banco:**
  - `catalog.products` — lock pessimista + atualização de `quantity`
  - `catalog.stock_reservations` — criação do registro de reserva
  - `catalog.stock_movements` — ledger imutável com `movement_type = RESERVE`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos esses arquivos **já existem** no projeto. Verificar apenas se precisam de novos métodos.

1. **[VERIFICAR]** `domain/.../catalog/entity/Product.java` — confirmar que `reserveStock(int qty)` existe e decrementa `this.quantity`.
2. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar que existe `findByIdForUpdate(ProductId id)` (lock pessimista). Se não existir, adicionar.
3. **[VERIFICAR]** `domain/.../catalog/gateway/StockReservationGateway.java` — já possui `save()`, `sumActiveQuantityByProduct()`. Sem alterações esperadas.
4. **[VERIFICAR]** `domain/.../catalog/gateway/StockMovementGateway.java` — já possui `save()`. Sem alterações esperadas.
5. **[VERIFICAR]** `domain/.../catalog/error/StockReservationError.java` — já possui `INSUFFICIENT_STOCK`. Confirmar.
6. **[VERIFICAR]** `domain/.../catalog/events/StockReservedEvent.java` — já existe.

### `application`

1. **[CRIAR]** `application/.../usecase/catalog/product/ReserveStockCommand.java`
2. **[CRIAR]** `application/.../usecase/catalog/product/ReserveStockOutput.java`
3. **[CRIAR]** `application/.../usecase/catalog/product/ReserveStockUseCase.java`

### `infrastructure`

1. **[VERIFICAR/ALTERAR]** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — adicionar `findByIdForUpdate()` com `@Lock(LockModeType.PESSIMISTIC_WRITE)` se não existir.
2. **[VERIFICAR/ALTERAR]** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — adicionar query com lock se necessário.
3. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockReservationJpaEntity.java` — confirmar `toAggregate()` e `from()`.
4. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockReservationPostgresGateway.java` — confirmar `save()`.
5. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockMovementPostgresGateway.java` — confirmar `save()`.

### `api`

1. **[CRIAR]** `api/.../catalog/ReserveStockRequest.java`
2. **[CRIAR]** `api/.../catalog/ReserveStockResponse.java`
3. **[ALTERAR]** `api/.../catalog/ProductController.java` — adicionar endpoint.
4. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean reserveStockUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Lock Pessimista no Gateway (Domain + Infrastructure)

O UC exige `SELECT FOR UPDATE` para garantir que dois checkouts simultâneos não reservem o mesmo estoque. Se `ProductGateway` não possuir o método, adicioná-lo:

```java
// ProductGateway.java (domain)
/**
 * Busca o produto pelo ID adquirindo lock pessimista (SELECT FOR UPDATE).
 * Usar exclusivamente em operações de escrita concorrentes de estoque.
 */
Optional<Product> findByIdForUpdate(ProductId id);
```

```java
// ProductJpaRepository.java (infrastructure)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
Optional<ProductJpaEntity> findByIdForUpdate(@Param("id") UUID id);
```

```java
// ProductPostgresGateway.java (infrastructure)
@Override
@Transactional   // escrita — herda da classe
public Optional<Product> findByIdForUpdate(final ProductId id) {
    return repository.findByIdForUpdate(id.getValue())
            .map(ProductJpaEntity::toAggregate);
}
```

### 2. Behavior no Aggregate `Product`

Verificar se `Product.reserveStock(int qty)` existe. Se não existir, criar:

```java
// Product.java — behavior a adicionar se ausente
public void reserveStock(final int qty) {
    if (qty <= 0) {
        throw DomainException.with(List.of(StockReservationError.QUANTITY_NOT_POSITIVE));
    }
    if (this.quantity < qty) {
        throw DomainException.with(List.of(StockReservationError.INSUFFICIENT_STOCK));
    }
    this.quantity -= qty;
    if (this.quantity == 0) {
        this.status = ProductStatus.OUT_OF_STOCK;
    }
    this.updatedAt = Instant.now();
    incrementVersion();
}
```

> Se o aggregate já tiver este método, ignorar e usar o existente.

### 3. Command e Output (Application)

**`ReserveStockCommand`**:
```java
package com.btree.application.usecase.catalog.product;

/**
 * Entrada para UC-71 — ReserveStock.
 *
 * @param productId UUID do produto como String
 * @param quantity  Quantidade a reservar (deve ser > 0)
 * @param orderId   UUID do pedido associado (opcional — pode ser null no pré-checkout)
 * @param ttlMinutes TTL da reserva em minutos (default: 15)
 */
public record ReserveStockCommand(
        String productId,
        int quantity,
        String orderId,
        int ttlMinutes
) {}
```

**`ReserveStockOutput`**:
```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockReservation;

import java.time.Instant;

/**
 * Saída para UC-71 — ReserveStock.
 */
public record ReserveStockOutput(
        String reservationId,
        String productId,
        int quantity,
        String orderId,
        Instant expiresAt,
        Instant createdAt,
        int quantityAfter
) {
    public static ReserveStockOutput from(
            final StockReservation reservation,
            final int quantityAfter
    ) {
        return new ReserveStockOutput(
                reservation.getId().getValue().toString(),
                reservation.getProductId().getValue().toString(),
                reservation.getQuantity(),
                reservation.getOrderId() != null ? reservation.getOrderId().toString() : null,
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                quantityAfter
        );
    }
}
```

### 4. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.entity.StockReservation;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.error.StockReservationError;
import com.btree.domain.catalog.events.StockReservedEvent;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.gateway.StockReservationGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * UC-71 — ReserveStock [CMD P0].
 *
 * <p>Reserva temporária de estoque usando lock pessimista (SELECT FOR UPDATE)
 * para prevenir venda a maior em cenários de alta concorrência.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Carregar produto com lock pessimista (findByIdForUpdate).</li>
 *   <li>Acumular erros de negócio (produto deletado, qty inválida, estoque insuficiente).</li>
 *   <li>Dentro da transação: decrementar quantity no produto, criar StockReservation,
 *       gravar StockMovement (RESERVE), publicar StockReservedEvent.</li>
 * </ol>
 */
public class ReserveStockUseCase implements UseCase<ReserveStockCommand, ReserveStockOutput> {

    private static final int DEFAULT_TTL_MINUTES = 15;

    private final ProductGateway           productGateway;
    private final StockReservationGateway  reservationGateway;
    private final StockMovementGateway     movementGateway;
    private final DomainEventPublisher     eventPublisher;
    private final TransactionManager       transactionManager;

    public ReserveStockUseCase(
            final ProductGateway productGateway,
            final StockReservationGateway reservationGateway,
            final StockMovementGateway movementGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.productGateway    = productGateway;
        this.reservationGateway = reservationGateway;
        this.movementGateway   = movementGateway;
        this.eventPublisher    = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, ReserveStockOutput> execute(final ReserveStockCommand command) {

        // 1. Carregar produto com lock pessimista — NotFoundException propaga como 404
        final var product = productGateway.findByIdForUpdate(ProductId.from(command.productId()))
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

        // 2. Acumular erros de negócio antes de entrar na transação
        final var notification = Notification.create();

        if (product.isDeleted()) {
            notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
        }

        if (command.quantity() <= 0) {
            notification.append(StockReservationError.QUANTITY_NOT_POSITIVE);
        }

        if (!notification.hasError() && product.getQuantity() < command.quantity()) {
            notification.append(StockReservationError.INSUFFICIENT_STOCK);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Parsear orderId opcional
        final UUID orderId;
        try {
            orderId = command.orderId() != null && !command.orderId().isBlank()
                    ? UUID.fromString(command.orderId())
                    : null;
        } catch (final IllegalArgumentException e) {
            notification.append(new com.btree.shared.validation.Error("'orderId' não é um UUID válido"));
            return Left(notification);
        }

        // 4. Calcular expiração
        final int ttl = command.ttlMinutes() > 0 ? command.ttlMinutes() : DEFAULT_TTL_MINUTES;
        final var expiresAt = Instant.now().plus(ttl, ChronoUnit.MINUTES);

        // 5. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 5a. Decrementar quantity no aggregate (pode mudar status para OUT_OF_STOCK)
            product.reserveStock(command.quantity());
            final var updatedProduct = productGateway.update(product);

            // 5b. Criar e persistir a reserva
            final var reservation = StockReservation.create(
                    updatedProduct.getId(),
                    orderId,
                    command.quantity(),
                    expiresAt
            );
            final var savedReservation = reservationGateway.save(reservation);

            // 5c. Gravar ledger imutável de movimentação
            final var movement = StockMovement.create(
                    updatedProduct.getId(),
                    StockMovementType.RESERVE,
                    -command.quantity(),  // delta negativo — saída de saldo disponível
                    savedReservation.getId().getValue(),
                    "STOCK_RESERVATION",
                    null
            );
            movementGateway.save(movement);

            // 5d. Publicar eventos do aggregate (ex.: ProductOutOfStockEvent)
            eventPublisher.publishAll(updatedProduct.getDomainEvents());

            // 5e. Publicar evento de reserva
            eventPublisher.publish(new StockReservedEvent(
                    savedReservation.getId().getValue().toString(),
                    updatedProduct.getId().getValue().toString(),
                    command.quantity(),
                    expiresAt,
                    orderId
            ));

            return ReserveStockOutput.from(savedReservation, updatedProduct.getQuantity());
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 5. Persistência (Infrastructure)

Verificar se `StockReservationJpaEntity` já existe. Se sim, confirmar que possui `toAggregate()` e `from(StockReservation)`. Estrutura esperada:

```java
@Entity
@Table(name = "stock_reservations", schema = "catalog")
public class StockReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "released", nullable = false)
    private boolean released;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockReservationJpaEntity from(final StockReservation reservation) { ... }

    public StockReservation toAggregate() { ... }
}
```

**`StockReservationPostgresGateway`** — deve implementar `sumActiveQuantityByProduct`:
```java
@Override
@Transactional(readOnly = true)
public int sumActiveQuantityByProduct(final ProductId productId) {
    // Soma quantity WHERE released=false AND confirmed=false AND expires_at > NOW()
    return repository.sumActiveQuantityByProductId(productId.getValue(), Instant.now())
            .orElse(0);
}
```

### 6. Roteamento e Injeção (API)

**`ReserveStockRequest`**:
```java
package com.btree.api.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;

/**
 * Request body para POST /api/v1/catalog/products/{productId}/stock/reservations.
 */
public record ReserveStockRequest(

        @NotNull(message = "'quantity' é obrigatório")
        @Min(value = 1, message = "'quantity' deve ser maior que zero")
        Integer quantity,

        String orderId,        // UUID como String — validado no use case

        @Min(value = 1, message = "'ttlMinutes' deve ser maior que zero")
        @Max(value = 60, message = "'ttlMinutes' deve ser no máximo 60 minutos")
        Integer ttlMinutes     // opcional; default = 15
) {}
```

**`ReserveStockResponse`**:
```java
package com.btree.api.catalog;

import com.btree.application.usecase.catalog.product.ReserveStockOutput;

import java.time.Instant;

public record ReserveStockResponse(
        String reservationId,
        String productId,
        int quantity,
        String orderId,
        Instant expiresAt,
        Instant createdAt,
        int quantityAfter
) {
    public static ReserveStockResponse from(final ReserveStockOutput output) {
        return new ReserveStockResponse(
                output.reservationId(),
                output.productId(),
                output.quantity(),
                output.orderId(),
                output.expiresAt(),
                output.createdAt(),
                output.quantityAfter()
        );
    }
}
```

**Endpoint no `ProductController`**:
```java
@PostMapping("/{productId}/stock/reservations")
@ResponseStatus(HttpStatus.CREATED)
@Operation(
        summary = "Reservar estoque",
        description = "Reserva temporária de estoque usando SELECT FOR UPDATE. " +
                      "A reserva expira automaticamente após o TTL (padrão: 15 min). " +
                      "Deve ser confirmada (UC-73) ou liberada (UC-72) após o checkout.")
@ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reserva criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Estoque insuficiente, produto deletado ou quantidade inválida")
})
public ReserveStockResponse reserveStock(
        @PathVariable final String productId,
        @Valid @RequestBody final ReserveStockRequest request
) {
    final var command = new ReserveStockCommand(
            productId,
            request.quantity(),
            request.orderId(),
            request.ttlMinutes() != null ? request.ttlMinutes() : 15
    );
    return ReserveStockResponse.from(
            reserveStockUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ReserveStockUseCase reserveStockUseCase(
        final ProductGateway productGateway,
        final StockReservationGateway stockReservationGateway,
        final StockMovementGateway stockMovementGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new ReserveStockUseCase(
            productGateway,
            stockReservationGateway,
            stockMovementGateway,
            eventPublisher,
            transactionManager
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP Resultante |
|---|---|---|---|
| Produto não encontrado | `NotFoundException` (lançada diretamente) | `findByIdForUpdate()` retorna `Optional.empty()` | `404 Not Found` |
| Produto soft-deletado | `ProductError.CANNOT_MODIFY_DELETED_PRODUCT` | `product.isDeleted() == true` | `422 Unprocessable Entity` |
| Quantity ≤ 0 | `StockReservationError.QUANTITY_NOT_POSITIVE` | `command.quantity() <= 0` | `422 Unprocessable Entity` |
| Estoque insuficiente | `StockReservationError.INSUFFICIENT_STOCK` | `product.getQuantity() < command.quantity()` | `422 Unprocessable Entity` |
| `orderId` inválido | `Error("'orderId' não é um UUID válido")` | UUID parse falha | `422 Unprocessable Entity` |
| Conflito de versão (lock) | `ObjectOptimisticLockingFailureException` | Outra transação modificou o produto no intervalo | `409 Conflict` |

> **Atenção ao lock pessimista**: `findByIdForUpdate()` deve ser chamado **dentro** da `@Transactional` para que o lock seja mantido até o `COMMIT`. Como o `TransactionManager` envolve o bloco `execute()`, o método deve ser chamado **dentro do `transactionManager.execute()`** — ajustar o algoritmo se necessário em caso de problemas de lock prematuro.

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products/{productId}/stock/reservations`

```json
{
  "quantity": 3,
  "orderId": "01965f3a-0000-7000-0000-000000000099",
  "ttlMinutes": 15
}
```

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `quantity` | `int` | Sim | ≥ 1 |
| `orderId` | `string (UUID)` | Não | UUID válido ou ausente (pré-checkout anônimo) |
| `ttlMinutes` | `int` | Não | 1–60; default = 15 |

### Response (Sucesso — 201 Created)

```json
{
  "reservationId": "01965f3a-0000-7000-0000-000000000060",
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "quantity": 3,
  "orderId": "01965f3a-0000-7000-0000-000000000099",
  "expiresAt": "2026-04-11T15:00:00Z",
  "createdAt": "2026-04-11T14:45:00Z",
  "quantityAfter": 7
}
```

> Quando a reserva zera o estoque: `quantityAfter = 0` e o produto transiciona para `OUT_OF_STOCK`.

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Estoque insuficiente para realizar a reserva"],
  "timestamp": "2026-04-11T14:45:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/stock/reservations"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Product.reserveStock(int qty)`** — criar se não existir (decrementa `quantity`, pode mudar status para `OUT_OF_STOCK`).
2. **Verificar `ProductGateway.findByIdForUpdate()`** — adicionar se não existir.
3. **Verificar `ProductJpaRepository.findByIdForUpdate()`** — adicionar query com `@Lock(PESSIMISTIC_WRITE)` se não existir.
4. **Verificar `ProductPostgresGateway.findByIdForUpdate()`** — adicionar implementação se não existir.
5. **`ReserveStockCommand`** — record com 4 campos.
6. **`ReserveStockOutput`** — record com factory `from(StockReservation, int quantityAfter)`.
7. **`ReserveStockUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
8. **`@Bean reserveStockUseCase`** em `UseCaseConfig`.
9. **`ReserveStockRequest`** — record com `@NotNull` e `@Min(1)` em `quantity`.
10. **`ReserveStockResponse`** — record com factory `from(ReserveStockOutput)`.
11. **Endpoint** `POST /{productId}/stock/reservations` no `ProductController`.
12. **Testes unitários** — `ReserveStockUseCaseTest` com Mockito (sem Spring):
    - quantidade válida e estoque suficiente → reserva criada, `quantityAfter` correto
    - quantidade válida e estoque zerado → `quantityAfter = 0`, produto `OUT_OF_STOCK`
    - `quantity <= 0` → `Left(notification)` com `QUANTITY_NOT_POSITIVE`
    - estoque insuficiente → `Left(notification)` com `INSUFFICIENT_STOCK`
    - produto deletado → `Left(notification)` com `CANNOT_MODIFY_DELETED_PRODUCT`
    - produto não encontrado → `NotFoundException` propagada (404)
    - `orderId` inválido (UUID malformado) → `Left(notification)` com erro de UUID
    - falha no `movementGateway.save()` → rollback completo (nenhuma linha persistida)
13. **Testes de integração** (`ReserveStockIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - Verificar que `catalog.stock_reservations` recebe a linha com `confirmed=false`, `released=false`.
    - Verificar que `catalog.products.quantity` é decrementado atomicamente.
    - Verificar que `catalog.stock_movements` registra linha com `movement_type = RESERVE`.
    - Teste de concorrência: duas threads tentando reservar o mesmo produto simultaneamente — apenas uma deve ter sucesso (ou ambas se o estoque permitir).
    - Falha em `movementGateway.save()` → rollback de `productGateway.update()` e `reservationGateway.save()`.
