# Task: UC-73 — ConfirmStockDeduction

## 📋 Resumo

Confirma uma reserva de estoque após o pagamento ser aprovado, transformando a dedução temporária (reserva) em definitiva. O estoque já foi decrementado em `catalog.products.quantity` pelo UC-71 (ReserveStock); portanto, este UC **não altera** o saldo do produto — apenas transiciona o estado da reserva de ativa para confirmada e registra o evento de ledger com `movement_type = CONFIRM`.

A operação é a etapa final do ciclo de vida de uma reserva e deve ser chamada pelo módulo de pagamentos após a confirmação de um pagamento aprovado.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/catalog/stock/reservations/{reservationId}/confirm` deve:

1. Carregar a `StockReservation` pelo ID.
2. Validar que a reserva está em estado confirmável: `confirmed=false`, `released=false` e **não expirada**.
3. Marcar a reserva como `confirmed=true` via behavior do aggregate (`reservation.confirm()`).
4. Gravar `StockMovement` do tipo `CONFIRM` (ledger imutável — sem alterar `quantity` no produto).
5. Publicar `StockConfirmedEvent` via Outbox.
6. Retornar `200 OK` com os dados da confirmação.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/stock/reservations/{reservationId}/confirm`
* **Tabelas do Banco:**
  - `catalog.stock_reservations` — atualização de `confirmed = true`
  - `catalog.stock_movements` — ledger com `movement_type = CONFIRM` (sem alterar `catalog.products`)

> **Importante:** `catalog.products.quantity` **não é alterado** neste UC. O estoque foi deduzido antecipadamente na reserva (UC-71). A confirmação apenas consolida o estado da reserva, sinalizando que a quantidade saiu definitivamente do estoque.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos esses arquivos **já existem**. Apenas verificações.

1. **[VERIFICAR]** `domain/.../catalog/entity/StockReservation.java` — confirmar que `confirm()` (a) lança `DomainException` se já `confirmed` ou `released`, e (b) registra `StockConfirmedEvent` via `registerEvent()`.
2. **[VERIFICAR]** `domain/.../catalog/error/StockReservationError.java` — confirmar `RESERVATION_NOT_FOUND`, `RESERVATION_ALREADY_CONFIRMED`, `RESERVATION_ALREADY_RELEASED`, `RESERVATION_EXPIRED`.
3. **[VERIFICAR]** `domain/.../catalog/gateway/StockReservationGateway.java` — confirmar `findById()` e `update()`.
4. **[VERIFICAR]** `domain/.../catalog/gateway/StockMovementGateway.java` — confirmar `save()`.
5. **[VERIFICAR]** `domain/.../catalog/events/StockConfirmedEvent.java` — já existe com `reservationId`, `productId`, `quantity`, `orderId`.

### `application`

1. **[CRIAR]** `application/.../usecase/catalog/product/ConfirmStockDeductionCommand.java`
2. **[CRIAR]** `application/.../usecase/catalog/product/ConfirmStockDeductionOutput.java`
3. **[CRIAR]** `application/.../usecase/catalog/product/ConfirmStockDeductionUseCase.java`

### `infrastructure`

1. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockReservationPostgresGateway.java` — confirmar `findById()` e `update()`.
2. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockReservationJpaEntity.java` — confirmar que `updateFrom()` copia `confirmed` e `released`.
3. **[VERIFICAR]** `infrastructure/.../catalog/persistence/StockMovementPostgresGateway.java` — confirmar `save()`.

### `api`

1. **[CRIAR]** `api/.../catalog/ConfirmStockDeductionResponse.java`
2. **[ALTERAR]** `api/.../catalog/ProductController.java` — adicionar endpoint (ou o `StockController` usado no UC-72).
3. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean confirmStockDeductionUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`ConfirmStockDeductionCommand`**:
```java
package com.btree.application.usecase.catalog.product;

/**
 * Entrada para UC-73 — ConfirmStockDeduction.
 *
 * @param reservationId UUID da reserva a ser confirmada
 */
public record ConfirmStockDeductionCommand(String reservationId) {}
```

**`ConfirmStockDeductionOutput`**:
```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockReservation;

import java.time.Instant;

/**
 * Saída para UC-73 — ConfirmStockDeduction.
 */
public record ConfirmStockDeductionOutput(
        String reservationId,
        String productId,
        int quantityConfirmed,
        String orderId,
        Instant confirmedAt
) {
    public static ConfirmStockDeductionOutput from(final StockReservation reservation) {
        return new ConfirmStockDeductionOutput(
                reservation.getId().getValue().toString(),
                reservation.getProductId().getValue().toString(),
                reservation.getQuantity(),
                reservation.getOrderId() != null ? reservation.getOrderId().toString() : null,
                Instant.now()
        );
    }
}
```

### 2. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.error.StockReservationError;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.gateway.StockReservationGateway;
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
 * UC-73 — ConfirmStockDeduction [CMD P0].
 *
 * <p>Confirma definitivamente a dedução de estoque reservado após o pagamento
 * ser aprovado. O saldo de {@code catalog.products.quantity} <b>não é alterado</b>
 * — já foi decrementado pelo UC-71 (ReserveStock).
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Carregar a {@code StockReservation} — {@code NotFoundException} se ausente.</li>
 *   <li>Validar estado: não pode estar confirmada, liberada ou expirada.</li>
 *   <li>Dentro da transação: marcar reserva como {@code confirmed}, gravar
 *       {@code StockMovement} (CONFIRM), publicar {@code StockConfirmedEvent}.</li>
 * </ol>
 *
 * <p><b>Idempotência:</b> reserva já confirmada retorna
 * {@code Left(notification)} com {@code RESERVATION_ALREADY_CONFIRMED}.
 */
public class ConfirmStockDeductionUseCase
        implements UseCase<ConfirmStockDeductionCommand, ConfirmStockDeductionOutput> {

    private final StockReservationGateway reservationGateway;
    private final StockMovementGateway    movementGateway;
    private final DomainEventPublisher    eventPublisher;
    private final TransactionManager      transactionManager;

    public ConfirmStockDeductionUseCase(
            final StockReservationGateway reservationGateway,
            final StockMovementGateway movementGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.reservationGateway = reservationGateway;
        this.movementGateway    = movementGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, ConfirmStockDeductionOutput> execute(
            final ConfirmStockDeductionCommand command
    ) {
        // 1. Carregar reserva — NotFoundException propaga como 404
        final var reservationId = StockReservationId.from(UUID.fromString(command.reservationId()));
        final var reservation = reservationGateway.findById(reservationId)
                .orElseThrow(() -> NotFoundException.with(StockReservationError.RESERVATION_NOT_FOUND));

        // 2. Validar estado — acumular todos os erros antes de entrar na transação
        final var notification = Notification.create();

        if (reservation.isConfirmed()) {
            notification.append(StockReservationError.RESERVATION_ALREADY_CONFIRMED);
        }
        if (reservation.isReleased()) {
            notification.append(StockReservationError.RESERVATION_ALREADY_RELEASED);
        }
        if (!reservation.isConfirmed() && !reservation.isReleased() && reservation.isExpired()) {
            notification.append(StockReservationError.RESERVATION_EXPIRED);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Persistir atomicamente (sem toque em catalog.products.quantity)
        return Try(() -> transactionManager.execute(() -> {

            // 3a. Transicionar estado (registra StockConfirmedEvent internamente)
            reservation.confirm();
            final var updatedReservation = reservationGateway.update(reservation);

            // 3b. Gravar ledger imutável — delta negativo confirma a dedução permanente
            final var movement = StockMovement.create(
                    updatedReservation.getProductId(),
                    StockMovementType.CONFIRM,
                    -updatedReservation.getQuantity(),  // negativo = saída definitiva confirmada
                    updatedReservation.getId().getValue(),
                    "STOCK_RESERVATION",
                    null
            );
            movementGateway.save(movement);

            // 3c. Publicar StockConfirmedEvent via Outbox
            eventPublisher.publishAll(updatedReservation.getDomainEvents());

            return ConfirmStockDeductionOutput.from(updatedReservation);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Nota sobre `StockMovementType.CONFIRM`**: verificar se o enum `StockMovementType` em `shared/enums/` possui o valor `CONFIRM`. Se não existir, adicionar (junto com `RESERVE` e `RELEASE`, se também ausentes).

### 3. Persistência (Infrastructure)

Não há JPA entities novas. Apenas verificações:

**`StockReservationJpaEntity.updateFrom()`** — deve copiar `confirmed` e `released` (mesmo já verificado no UC-72):
```java
public void updateFrom(final StockReservation aggregate) {
    this.confirmed = aggregate.isConfirmed();
    this.released  = aggregate.isReleased();
    // id, productId, orderId, quantity, expiresAt e createdAt jamais sobrescritos
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

**`ConfirmStockDeductionResponse`**:
```java
package com.btree.api.catalog;

import com.btree.application.usecase.catalog.product.ConfirmStockDeductionOutput;

import java.time.Instant;

public record ConfirmStockDeductionResponse(
        String reservationId,
        String productId,
        int quantityConfirmed,
        String orderId,
        Instant confirmedAt
) {
    public static ConfirmStockDeductionResponse from(final ConfirmStockDeductionOutput output) {
        return new ConfirmStockDeductionResponse(
                output.reservationId(),
                output.productId(),
                output.quantityConfirmed(),
                output.orderId(),
                output.confirmedAt()
        );
    }
}
```

**Endpoint** — adicionar ao mesmo controller usado no UC-72:
```java
@PostMapping("/stock/reservations/{reservationId}/confirm")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Confirmar dedução de estoque",
        description = "Confirma definitivamente a dedução de estoque reservado após " +
                      "pagamento aprovado. Não altera o saldo do produto — apenas " +
                      "consolida o estado da reserva. Deve ser chamado pelo módulo de " +
                      "pagamentos após aprovação.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dedução confirmada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Reserva não encontrada"),
        @ApiResponse(responseCode = "422", description = "Reserva já confirmada, já liberada ou expirada")
})
public ConfirmStockDeductionResponse confirmStockDeduction(
        @PathVariable final String reservationId
) {
    final var command = new ConfirmStockDeductionCommand(reservationId);
    return ConfirmStockDeductionResponse.from(
            confirmStockDeductionUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> Sem `@RequestBody` — o único dado necessário é o `reservationId` no path.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ConfirmStockDeductionUseCase confirmStockDeductionUseCase(
        final StockReservationGateway stockReservationGateway,
        final StockMovementGateway stockMovementGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new ConfirmStockDeductionUseCase(
            stockReservationGateway,
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
| Reserva já confirmada | `StockReservationError.RESERVATION_ALREADY_CONFIRMED` | `reservation.isConfirmed() == true` | `422 Unprocessable Entity` |
| Reserva já liberada | `StockReservationError.RESERVATION_ALREADY_RELEASED` | `reservation.isReleased() == true` | `422 Unprocessable Entity` |
| Reserva expirada | `StockReservationError.RESERVATION_EXPIRED` | `reservation.isExpired() == true` (e ainda ativa) | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | Outra transação modificou a reserva | `409 Conflict` |

> **Diferença crucial em relação ao UC-72 (ReleaseStock):** a reserva **expirada é um erro** na confirmação. Não faz sentido confirmar o pagamento de uma reserva que expirou — o carrinho deve ter sido reiniciado e o fluxo de checkout retomado do zero (nova reserva).

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/stock/reservations/{reservationId}/confirm`

Sem body. O `reservationId` é passado no path.

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `reservationId` | `UUID` (path) | Sim | UUID válido da reserva |

### Response (Sucesso — 200 OK)

```json
{
  "reservationId": "01965f3a-0000-7000-0000-000000000060",
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "quantityConfirmed": 3,
  "orderId": "01965f3a-0000-7000-0000-000000000099",
  "confirmedAt": "2026-04-11T15:10:00Z"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Reserva de estoque expirada"],
  "timestamp": "2026-04-11T15:10:00Z",
  "path": "/api/v1/catalog/stock/reservations/01965f3a-0000-7000-0000-000000000060/confirm"
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Reserva de estoque não encontrada",
  "timestamp": "2026-04-11T15:10:00Z",
  "path": "/api/v1/catalog/stock/reservations/01965f3a-0000-7000-0000-000000000060/confirm"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `StockMovementType` enum** — confirmar que `CONFIRM` existe. Se não, adicionar.
2. **Verificar `StockReservation.confirm()`** — confirmar que registra `StockConfirmedEvent` e lança `DomainException` para estados inválidos.
3. **Verificar `StockReservationJpaEntity.updateFrom()`** — confirmar que copia `confirmed`.
4. **Verificar `StockReservationPostgresGateway.update()`** — confirmar implementação.
5. **`ConfirmStockDeductionCommand`** — record simples com `reservationId`.
6. **`ConfirmStockDeductionOutput`** — record com factory `from(StockReservation)`.
7. **`ConfirmStockDeductionUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
8. **`@Bean confirmStockDeductionUseCase`** em `UseCaseConfig`.
9. **`ConfirmStockDeductionResponse`** — record com factory `from(ConfirmStockDeductionOutput)`.
10. **Endpoint** `POST /stock/reservations/{reservationId}/confirm` no controller.
11. **Testes unitários** — `ConfirmStockDeductionUseCaseTest` com Mockito (sem Spring):
    - reserva ativa e válida → confirmada, `StockConfirmedEvent` publicado, movement gravado
    - reserva já confirmada → `Left(notification)` com `RESERVATION_ALREADY_CONFIRMED`
    - reserva já liberada → `Left(notification)` com `RESERVATION_ALREADY_RELEASED`
    - reserva expirada → `Left(notification)` com `RESERVATION_EXPIRED`
    - reserva não encontrada → `NotFoundException` propagada (404)
    - falha em `movementGateway.save()` → rollback de `reservationGateway.update()`
    - confirmar que `productGateway` **nunca é chamado** (quantity do produto não muda)
12. **Testes de integração** (`ConfirmStockDeductionIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `catalog.stock_reservations.confirmed = true` após execução.
    - `catalog.products.quantity` **permanece inalterado** (verificação crítica).
    - `catalog.stock_movements` registra linha com `movement_type = CONFIRM` e `quantity` negativo.
    - Falha forçada em `movementGateway.save()` faz rollback de `reservationGateway.update()`.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-71 ReserveStock** | Cria a `StockReservation` e decrementa o saldo do produto antecipadamente |
| **UC-72 ReleaseStock** | Alternativa: libera a reserva sem confirmar (cancelamento/expiração) |
| **UC-76 CleanupExpiredReservations** | Job que libera (UC-72) automaticamente reservas não confirmadas a tempo |

### Ciclo completo de vida de uma reserva

```
[UC-71 ReserveStock]
        │
        ▼
  reservation.active
  product.quantity -= qty
        │
        ├──── pagamento aprovado ────► [UC-73 ConfirmStockDeduction]
        │                                    confirmed = true
        │                                    quantity inalterado
        │
        └──── cancelamento / expiração ─► [UC-72 ReleaseStock]
                                               released = true
                                               product.quantity += qty
```
