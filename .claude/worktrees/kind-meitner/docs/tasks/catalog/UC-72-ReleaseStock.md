# Task: UC-72 — ReleaseStock

## 📋 Resumo

Libera uma reserva de estoque, devolvendo a quantidade reservada ao saldo disponível do produto. O gatilho pode ser:

- **Expiração automática** — reserva atingiu o TTL sem ser confirmada (chamada pelo job `CleanupExpiredReservations` — UC-76).
- **Cancelamento explícito** — cliente cancelou o carrinho ou o pedido foi recusado antes do pagamento.

A operação é idempotente: tentar liberar uma reserva já liberada ou já confirmada resulta em erro de negócio sem side effects.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/catalog/stock/reservations/{reservationId}/release` deve:

1. Carregar a `StockReservation` pelo ID.
2. Validar que a reserva está em estado liberável (`confirmed=false` e `released=false`).
3. Marcar a reserva como `released=true` via behavior do aggregate (`reservation.release()`).
4. Incrementar `catalog.products.quantity` devolvendo a quantidade reservada (`product.addStock(qty)`).
5. Gravar `StockMovement` do tipo `RELEASE` (ledger imutável).
6. Publicar `StockReleasedEvent` via Outbox.
7. Retornar `200 OK` com os dados da reserva liberada.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/stock/reservations/{reservationId}/release`
* **Tabelas do Banco:**
  - `catalog.stock_reservations` — atualização de `released = true`
  - `catalog.products` — incremento de `quantity` (devolução)
  - `catalog.stock_movements` — ledger com `movement_type = RELEASE`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos esses arquivos **já existem** no projeto. Apenas verificações.

1. **[VERIFICAR]** `domain/.../catalog/entity/StockReservation.java` — confirmar que `release()` registra `StockReleasedEvent` e lança `DomainException` se já `released` ou `confirmed`.
2. **[VERIFICAR]** `domain/.../catalog/entity/Product.java` — confirmar que `addStock(int delta)` existe e reverte o status de `OUT_OF_STOCK` para `ACTIVE` quando `quantity > 0`.
3. **[VERIFICAR]** `domain/.../catalog/gateway/StockReservationGateway.java` — confirmar `findById()` e `update()`.
4. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar `findById()` e `update()`.
5. **[VERIFICAR]** `domain/.../catalog/gateway/StockMovementGateway.java` — confirmar `save()`.
6. **[VERIFICAR]** `domain/.../catalog/error/StockReservationError.java` — confirmar `RESERVATION_NOT_FOUND`, `RESERVATION_ALREADY_RELEASED`, `RESERVATION_ALREADY_CONFIRMED`.
7. **[VERIFICAR]** `domain/.../catalog/events/StockReleasedEvent.java` — já existe.

### `application`

1. **[CRIAR]** `application/.../usecase/catalog/product/ReleaseStockCommand.java`
2. **[CRIAR]** `application/.../usecase/catalog/product/ReleaseStockOutput.java`
3. **[CRIAR]** `application/.../usecase/catalog/product/ReleaseStockUseCase.java`

### `infrastructure`

1. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockReservationPostgresGateway.java` — confirmar `findById()` e `update()`.
2. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockMovementPostgresGateway.java` — confirmar `save()`.
3. **[VERIFICAR]** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — confirmar `findById()` e `update()`.

### `api`

1. **[CRIAR]** `api/.../catalog/ReleaseStockResponse.java`
2. **[ALTERAR]** `api/.../catalog/ProductController.java` — adicionar endpoint (ou criar `StockController` se preferir separação).
3. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean releaseStockUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`ReleaseStockCommand`**:
```java
package com.btree.application.usecase.catalog.product;

/**
 * Entrada para UC-72 — ReleaseStock.
 *
 * @param reservationId UUID da reserva a ser liberada
 */
public record ReleaseStockCommand(String reservationId) {}
```

**`ReleaseStockOutput`**:
```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockReservation;

import java.time.Instant;

/**
 * Saída para UC-72 — ReleaseStock.
 */
public record ReleaseStockOutput(
        String reservationId,
        String productId,
        int quantityReleased,
        int quantityAfter,
        Instant releasedAt
) {
    public static ReleaseStockOutput from(
            final StockReservation reservation,
            final int quantityAfter
    ) {
        return new ReleaseStockOutput(
                reservation.getId().getValue().toString(),
                reservation.getProductId().getValue().toString(),
                reservation.getQuantity(),
                quantityAfter,
                Instant.now()
        );
    }
}
```

### 2. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.error.StockReservationError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.gateway.StockReservationGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.identifier.StockReservationId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * UC-72 — ReleaseStock [CMD P0].
 *
 * <p>Libera uma reserva de estoque (expirada ou cancelada), devolvendo
 * a quantidade reservada ao saldo disponível do produto.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Carregar a {@code StockReservation} — {@code NotFoundException} se ausente.</li>
 *   <li>Carregar o {@code Product} associado — {@code NotFoundException} se ausente.</li>
 *   <li>Validar estado da reserva: não pode estar já liberada ou confirmada.</li>
 *   <li>Dentro da transação: marcar reserva como {@code released}, devolver estoque
 *       ao produto, gravar {@code StockMovement} (RELEASE), publicar eventos.</li>
 * </ol>
 *
 * <p><b>Idempotência:</b> Tentar liberar uma reserva já liberada retorna
 * {@code Left(notification)} com {@code RESERVATION_ALREADY_RELEASED}.
 */
public class ReleaseStockUseCase implements UseCase<ReleaseStockCommand, ReleaseStockOutput> {

    private final StockReservationGateway reservationGateway;
    private final ProductGateway          productGateway;
    private final StockMovementGateway    movementGateway;
    private final DomainEventPublisher    eventPublisher;
    private final TransactionManager      transactionManager;

    public ReleaseStockUseCase(
            final StockReservationGateway reservationGateway,
            final ProductGateway productGateway,
            final StockMovementGateway movementGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.reservationGateway = reservationGateway;
        this.productGateway     = productGateway;
        this.movementGateway    = movementGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, ReleaseStockOutput> execute(final ReleaseStockCommand command) {

        // 1. Carregar reserva — NotFoundException propaga como 404
        final var reservationId = StockReservationId.from(UUID.fromString(command.reservationId()));
        final var reservation = reservationGateway.findById(reservationId)
                .orElseThrow(() -> NotFoundException.with(StockReservationError.RESERVATION_NOT_FOUND));

        // 2. Validar estado — acumular erros antes de entrar na transação
        final var notification = Notification.create();

        if (reservation.isReleased()) {
            notification.append(StockReservationError.RESERVATION_ALREADY_RELEASED);
        }
        if (reservation.isConfirmed()) {
            notification.append(StockReservationError.RESERVATION_ALREADY_CONFIRMED);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Carregar produto — NotFoundException propaga como 404
        final var product = productGateway.findById(reservation.getProductId())
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

        // 4. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 4a. Marcar reserva como liberada (registra StockReleasedEvent internamente)
            reservation.release();
            final var updatedReservation = reservationGateway.update(reservation);

            // 4b. Devolver quantidade ao saldo do produto
            //     addStock() reverte O status OUT_OF_STOCK → ACTIVE se quantity voltar > 0
            product.addStock(updatedReservation.getQuantity());
            final var updatedProduct = productGateway.update(product);

            // 4c. Gravar ledger imutável de movimentação
            final var movement = StockMovement.create(
                    updatedProduct.getId(),
                    StockMovementType.RELEASE,
                    updatedReservation.getQuantity(),  // delta positivo — entrada de saldo
                    updatedReservation.getId().getValue(),
                    "STOCK_RESERVATION",
                    null
            );
            movementGateway.save(movement);

            // 4d. Publicar eventos do aggregate StockReservation (StockReleasedEvent)
            eventPublisher.publishAll(updatedReservation.getDomainEvents());

            // 4e. Publicar eventos do aggregate Product (ex.: ProductBackInStockEvent, se houver)
            eventPublisher.publishAll(updatedProduct.getDomainEvents());

            return ReleaseStockOutput.from(updatedReservation, updatedProduct.getQuantity());
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Nota sobre `StockReservationId.from()`**: verificar se o identifier já possui o método `from(UUID)`. Se não, usar o padrão do projeto (`new StockReservationId(uuid)` ou `StockReservationId.of(uuid)`).

### 3. Persistência (Infrastructure)

Não há JPA entities novas. Verificar apenas:

**`StockReservationJpaEntity.updateFrom(StockReservation)`** — deve copiar `released` e `confirmed`:
```java
public void updateFrom(final StockReservation aggregate) {
    this.confirmed = aggregate.isConfirmed();
    this.released  = aggregate.isReleased();
    // id e createdAt jamais sobrescritos
}
```

**`StockReservationPostgresGateway.update()`** — padrão esperado:
```java
@Override
public StockReservation update(final StockReservation reservation) {
    final var entity = repository.findById(reservation.getId().getValue())
            .orElseThrow(() -> NotFoundException.with(StockReservationError.RESERVATION_NOT_FOUND));
    entity.updateFrom(reservation);
    return repository.save(entity).toAggregate();
}
```

### 4. Roteamento e Injeção (API)

**`ReleaseStockResponse`**:
```java
package com.btree.api.catalog;

import com.btree.application.usecase.catalog.product.ReleaseStockOutput;

import java.time.Instant;

public record ReleaseStockResponse(
        String reservationId,
        String productId,
        int quantityReleased,
        int quantityAfter,
        Instant releasedAt
) {
    public static ReleaseStockResponse from(final ReleaseStockOutput output) {
        return new ReleaseStockResponse(
                output.reservationId(),
                output.productId(),
                output.quantityReleased(),
                output.quantityAfter(),
                output.releasedAt()
        );
    }
}
```

**Endpoint** — adicionar ao `ProductController` (ou a um `StockController` dedicado):
```java
@PostMapping("/stock/reservations/{reservationId}/release")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Liberar reserva de estoque",
        description = "Libera uma reserva expirada ou cancelada, devolvendo a quantidade " +
                      "ao saldo disponível do produto. Operação idempotente — retorna erro " +
                      "se a reserva já foi liberada ou confirmada.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reserva liberada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Reserva ou produto não encontrado"),
        @ApiResponse(responseCode = "422", description = "Reserva já liberada ou já confirmada")
})
public ReleaseStockResponse releaseStock(
        @PathVariable final String reservationId
) {
    final var command = new ReleaseStockCommand(reservationId);
    return ReleaseStockResponse.from(
            releaseStockUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> O endpoint não possui `@RequestBody` pois o único dado necessário é o `reservationId` no path.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ReleaseStockUseCase releaseStockUseCase(
        final StockReservationGateway stockReservationGateway,
        final ProductGateway productGateway,
        final StockMovementGateway stockMovementGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new ReleaseStockUseCase(
            stockReservationGateway,
            productGateway,
            stockMovementGateway,
            eventPublisher,
            transactionManager
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| Reserva não encontrada | `NotFoundException` (lançada diretamente) | `reservationGateway.findById()` vazio | `404 Not Found` |
| Produto não encontrado | `NotFoundException` (lançada diretamente) | `productGateway.findById()` vazio | `404 Not Found` |
| Reserva já liberada | `StockReservationError.RESERVATION_ALREADY_RELEASED` | `reservation.isReleased() == true` | `422 Unprocessable Entity` |
| Reserva já confirmada | `StockReservationError.RESERVATION_ALREADY_CONFIRMED` | `reservation.isConfirmed() == true` | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | Outra transação modificou o produto no intervalo | `409 Conflict` |

> **Reserva expirada não é erro**: uma reserva com `expires_at < now()` e `released=false` ainda pode (e deve) ser liberada. A flag `isExpired()` do aggregate é informativa — apenas converte para erro quando a tentativa de **confirmar** (UC-73) uma reserva expirada.

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/stock/reservations/{reservationId}/release`

Sem body. O `reservationId` é passado no path.

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `reservationId` | `UUID` (path) | Sim | UUID válido da reserva |

### Response (Sucesso — 200 OK)

```json
{
  "reservationId": "01965f3a-0000-7000-0000-000000000060",
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "quantityReleased": 3,
  "quantityAfter": 10,
  "releasedAt": "2026-04-11T15:05:00Z"
}
```

> Quando a devolução repõe o estoque de um produto `OUT_OF_STOCK`: `quantityAfter > 0` e o produto volta ao status `ACTIVE` automaticamente via `product.addStock()`.

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Reserva já foi liberada"],
  "timestamp": "2026-04-11T15:05:00Z",
  "path": "/api/v1/catalog/stock/reservations/01965f3a-0000-7000-0000-000000000060/release"
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Reserva de estoque não encontrada",
  "timestamp": "2026-04-11T15:05:00Z",
  "path": "/api/v1/catalog/stock/reservations/01965f3a-0000-7000-0000-000000000060/release"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `StockReservation.release()`** — confirmar que registra `StockReleasedEvent` via `registerEvent()` e lança `DomainException` para estados inválidos.
2. **Verificar `StockReservationJpaEntity.updateFrom()`** — confirmar que copia `released` e `confirmed`.
3. **Verificar `StockReservationPostgresGateway.update()`** — confirmar implementação.
4. **`ReleaseStockCommand`** — record simples com `reservationId`.
5. **`ReleaseStockOutput`** — record com factory `from(StockReservation, int quantityAfter)`.
6. **`ReleaseStockUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
7. **`@Bean releaseStockUseCase`** em `UseCaseConfig`.
8. **`ReleaseStockResponse`** — record com factory `from(ReleaseStockOutput)`.
9. **Endpoint** `POST /stock/reservations/{reservationId}/release` no controller.
10. **Testes unitários** — `ReleaseStockUseCaseTest` com Mockito (sem Spring):
    - reserva ativa → liberada, `quantityAfter` correto, `StockReleasedEvent` publicado
    - reserva ativa + produto estava `OUT_OF_STOCK` → `quantityAfter > 0`, produto volta a `ACTIVE`
    - reserva já liberada (`released=true`) → `Left(notification)` com `RESERVATION_ALREADY_RELEASED`
    - reserva já confirmada (`confirmed=true`) → `Left(notification)` com `RESERVATION_ALREADY_CONFIRMED`
    - reserva expirada mas não liberada → liberada com sucesso (expiração não é impedimento)
    - reserva não encontrada → `NotFoundException` propagada (404)
    - produto não encontrado → `NotFoundException` propagada (404)
    - falha no `movementGateway.save()` → rollback completo de `reservationGateway.update()` e `productGateway.update()`
11. **Testes de integração** (`ReleaseStockIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `catalog.stock_reservations.released` = `true` após execução.
    - `catalog.products.quantity` incrementado corretamente.
    - `catalog.stock_movements` registra linha com `movement_type = RELEASE` e `quantity` positivo.
    - Falha forçada em `movementGateway.save()` faz rollback de todas as alterações.
    - Produto `OUT_OF_STOCK` com reserva liberada → volta a `ACTIVE`.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-71 ReserveStock** | Cria a `StockReservation` que este UC libera |
| **UC-73 ConfirmStockDeduction** | Alternativa à liberação — confirma a reserva após pagamento aprovado |
| **UC-76 CleanupExpiredReservations** | Job que chama este UC (ou lógica equivalente) em lote para liberar reservas expiradas automaticamente |
