# Task: UC-70 — AdjustStock

## 📋 Resumo
Permite que operadores administrativos registrem uma **entrada ou saída manual de estoque** para um produto, corrigindo o saldo físico após contagem, recebimento de mercadoria avulsa, baixa por dano ou qualquer outra razão operacional. A operação grava um registro imutável na tabela `catalog.stock_movements` (ledger de auditoria) **e** atualiza o campo `quantity` do produto de forma atômica na mesma transação, preservando as transições de status automáticas já definidas no domínio (`ACTIVE ↔ OUT_OF_STOCK`).

## 🎯 Objetivo
Ao receber um `POST /api/v1/catalog/products/{productId}/stock/adjustments`, o sistema deve:
1. Verificar que o produto existe e não está soft-deletado;
2. Validar que o `delta` é diferente de zero e que o `movementType` foi informado;
3. Se `delta < 0`: verificar estoque suficiente para a dedução;
4. Aplicar a mutação no `Product` via `addStock(delta)` ou `deductStock(-delta)`, acionando automaticamente a transição de status quando necessário;
5. Persistir o `Product` atualizado e o novo `StockMovement` em **uma única transação**;
6. Publicar os domain events registrados no aggregate (ex.: `ProductOutOfStockEvent`);
7. Retornar o ID do movimento criado, o saldo após o ajuste e o status atual do produto.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (command), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `POST /api/v1/catalog/products/{productId}/stock/adjustments`
* **Tabelas do Banco:** `catalog.products`, `catalog.stock_movements` (particionada por trimestre)

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **CRIAR** `domain/.../catalog/events/StockAdjustedEvent.java` — domain event publicado após a conclusão do ajuste.

### `application`
1. **CRIAR** `application/.../catalog/product/AdjustStockCommand.java` — record com `productId`, `delta`, `movementType`, `notes`, `referenceId`, `referenceType`.
2. **CRIAR** `application/.../catalog/product/AdjustStockOutput.java` — record com `movementId`, `productId`, `movementType`, `delta`, `quantityAfter`, `productStatus`, `createdAt`.
3. **CRIAR** `application/.../catalog/product/AdjustStockUseCase.java` — `UseCase<AdjustStockCommand, AdjustStockOutput>`.

### `infrastructure`
> Nenhum arquivo novo necessário — `StockMovementJpaEntity`, `StockMovementJpaRepository` e `StockMovementPostgresGateway` já existem. `ProductJpaEntity`, `ProductJpaRepository` e `ProductPostgresGateway` também já existem e têm suporte a `update()`.

### `api`
1. **CRIAR** `api/.../catalog/AdjustStockRequest.java` — record com Bean Validation.
2. **CRIAR** `api/.../catalog/AdjustStockResponse.java` — record com factory `from(AdjustStockOutput)`.
3. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `POST /{productId}/stock/adjustments`.
4. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `AdjustStockUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Domain Event — `StockAdjustedEvent`

```java
package com.btree.domain.catalog.events;

import com.btree.shared.domain.DomainEvent;

/**
 * Publicado quando um ajuste manual de estoque é realizado com sucesso.
 */
public class StockAdjustedEvent extends DomainEvent {

    private final String productId;
    private final int delta;
    private final int quantityAfter;
    private final String movementType;

    public StockAdjustedEvent(
            final String productId,
            final int delta,
            final int quantityAfter,
            final String movementType
    ) {
        super("stock.adjusted", "Product");
        this.productId     = productId;
        this.delta         = delta;
        this.quantityAfter = quantityAfter;
        this.movementType  = movementType;
    }

    public String getProductId()     { return productId; }
    public int getDelta()            { return delta; }
    public int getQuantityAfter()    { return quantityAfter; }
    public String getMovementType()  { return movementType; }
}
```

> Verificar como outros eventos do catálogo constroem o `super(eventType, aggregateType)` (ex.: `ProductPublishedEvent`, `ProductArchivedEvent`) e replicar exatamente o mesmo padrão. A publicação é feita via `eventPublisher.publishAll(List.of(new StockAdjustedEvent(...)))` **após** o `productGateway.update()` e `stockMovementGateway.save()`, já que `StockMovement` não é um Aggregate Root e não tem `getDomainEvents()`.

### 2. Contrato de Entrada/Saída (Application)

**`AdjustStockCommand`**:
```java
public record AdjustStockCommand(
        String productId,
        int delta,              // positivo = entrada, negativo = saída
        String movementType,    // nome do enum StockMovementType: IN, OUT, ADJUSTMENT, RETURN
        String notes,           // opcional — motivo do ajuste
        String referenceId,     // opcional — UUID como String (NF, OS, etc.)
        String referenceType    // opcional — descrição do sistema de origem
) {}
```

**`AdjustStockOutput`**:
```java
public record AdjustStockOutput(
        String movementId,
        String productId,
        String movementType,
        int delta,
        int quantityAfter,
        String productStatus,   // status do produto após o ajuste
        String createdAt        // ISO-8601
) {
    public static AdjustStockOutput from(final Product product, final StockMovement movement) {
        return new AdjustStockOutput(
                movement.getId().getValue().toString(),
                product.getId().getValue().toString(),
                movement.getMovementType().name(),
                movement.getQuantity(),
                product.getQuantity(),
                product.getStatus().name(),
                movement.getCreatedAt().toString()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
public class AdjustStockUseCase implements UseCase<AdjustStockCommand, AdjustStockOutput> {

    private final ProductGateway        productGateway;
    private final StockMovementGateway  stockMovementGateway;
    private final DomainEventPublisher  eventPublisher;
    private final TransactionManager    transactionManager;

    // construtor com os quatro colaboradores

    @Override
    public Either<Notification, AdjustStockOutput> execute(final AdjustStockCommand command) {

        // 1. Carregar produto — NotFoundException propaga como 404
        final var product = productGateway.findById(ProductId.from(command.productId()))
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

        // 2. Acumular erros de negócio antes de persistir
        final var notification = Notification.create();

        if (product.isDeleted()) {
            notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
        }

        if (command.delta() == 0) {
            notification.append(StockMovementError.QUANTITY_ZERO);
        }

        final var movementType = parseMovementType(command.movementType(), notification);

        if (!notification.hasError() && command.delta() < 0
                && product.getQuantity() < Math.abs(command.delta())) {
            notification.append(StockMovementError.INSUFFICIENT_STOCK);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Aplicar mutação e persistir atomicamente
        final var referenceId = command.referenceId() != null
                ? UUID.fromString(command.referenceId())
                : null;

        return Try(() -> transactionManager.execute(() -> {

            // 3a. Mutação no aggregate (dispara transição de status automática se aplicável)
            if (command.delta() > 0) {
                product.addStock(command.delta());
            } else {
                product.deductStock(Math.abs(command.delta()));
            }

            // 3b. Persiste produto com novo quantity, status e version
            final var updatedProduct = productGateway.update(product);

            // 3c. Cria e persiste o registro de movimentação (ledger imutável)
            final var movement = StockMovement.create(
                    updatedProduct.getId(),
                    movementType,
                    command.delta(),
                    referenceId,
                    command.referenceType(),
                    command.notes()
            );
            final var savedMovement = stockMovementGateway.save(movement);

            // 3d. Publica eventos do aggregate + evento de ajuste
            eventPublisher.publishAll(updatedProduct.getDomainEvents());
            eventPublisher.publishAll(List.of(new StockAdjustedEvent(
                    updatedProduct.getId().getValue().toString(),
                    command.delta(),
                    updatedProduct.getQuantity(),
                    movementType.name()
            )));

            return AdjustStockOutput.from(updatedProduct, savedMovement);

        })).toEither().mapLeft(Notification::create);
    }

    /**
     * Converte o nome textual do movementType para o enum, acumulando erro se inválido.
     */
    private StockMovementType parseMovementType(
            final String raw,
            final Notification notification
    ) {
        if (raw == null || raw.isBlank()) {
            notification.append(StockMovementError.MOVEMENT_TYPE_NULL);
            return null;
        }
        try {
            return StockMovementType.valueOf(raw.toUpperCase());
        } catch (final IllegalArgumentException e) {
            notification.append(StockMovementError.MOVEMENT_TYPE_NULL);
            return null;
        }
    }
}
```

> **Importante sobre `addStock` / `deductStock`:** ambos já chamam `incrementVersion()` internamente. O `ProductPostgresGateway.update()` propaga o campo `version` do aggregate para a JPA entity via `updateFrom()`. O Hibernate incrementa o campo `@Version` automaticamente no flush — não há double-increment porque `@Version` é gerenciado pelo JPA, não pelo campo `version` do domínio.

> **`DomainEventPublisher.publishAll(List)`:** verificar a assinatura exata da interface em `shared`. Se o método aceitar `Collection<DomainEvent>`, usar `List.of(...)`. Se aceitar `List<?>` com wildcard, adaptar conforme necessário.

### 4. Persistência (Infrastructure)

Nenhum arquivo novo necessário. Verificar:

- `ProductPostgresGateway.update()` — já usa `entity.updateFrom(product)` que preserva `id` e `version`. O campo `quantity` e `status` precisam ser copiados em `updateFrom()`. Confirmar que ambos estão incluídos (ver `ProductJpaEntity.updateFrom()`).
- `StockMovementPostgresGateway.save()` — já implementado. O composite PK `(id, createdAt)` é populado via `StockMovementJpaEntity.from(movement)`.

### 5. Roteamento e Injeção (API)

**`AdjustStockRequest`**:
```java
public record AdjustStockRequest(
        @NotNull(message = "'delta' é obrigatório")
        Integer delta,

        @NotBlank(message = "'movementType' é obrigatório")
        String movementType,    // IN | OUT | ADJUSTMENT | RETURN

        @Size(max = 1000, message = "'notes' deve ter no máximo 1000 caracteres")
        String notes,

        String referenceId,     // UUID como String — validado no use case

        @Size(max = 50, message = "'referenceType' deve ter no máximo 50 caracteres")
        String referenceType
) {}
```

**`AdjustStockResponse`**:
```java
public record AdjustStockResponse(
        String movementId,
        String productId,
        String movementType,
        int delta,
        int quantityAfter,
        String productStatus,
        String createdAt
) {
    public static AdjustStockResponse from(final AdjustStockOutput output) {
        return new AdjustStockResponse(
                output.movementId(),
                output.productId(),
                output.movementType(),
                output.delta(),
                output.quantityAfter(),
                output.productStatus(),
                output.createdAt()
        );
    }
}
```

**Endpoint no `ProductController`**:
```java
@PostMapping("/{productId}/stock/adjustments")
@ResponseStatus(HttpStatus.CREATED)
@Operation(
        summary = "Ajuste manual de estoque",
        description = "Registra uma entrada ou saída manual de estoque. " +
                      "delta > 0 = entrada; delta < 0 = saída. " +
                      "Atualiza o saldo do produto e grava um registro de movimentação imutável.")
@ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ajuste realizado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Produto deletado, delta zero, tipo inválido ou estoque insuficiente")
})
public AdjustStockResponse adjustStock(
        @PathVariable final String productId,
        @Valid @RequestBody final AdjustStockRequest request
) {
    final var command = new AdjustStockCommand(
            productId,
            request.delta(),
            request.movementType(),
            request.notes(),
            request.referenceId(),
            request.referenceType()
    );
    return AdjustStockResponse.from(
            adjustStockUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public AdjustStockUseCase adjustStockUseCase(
        final ProductGateway productGateway,
        final StockMovementGateway stockMovementGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new AdjustStockUseCase(
            productGateway, stockMovementGateway, eventPublisher, transactionManager);
}
```

> Verificar se `StockMovementGateway` já está sendo injetado como `@Component` / `@Bean` no contexto Spring. Se não houver `@Bean` explícito em `UseCaseConfig` para ele (porque o gateway concreto `StockMovementPostgresGateway` já tem `@Component`), o Spring injeta automaticamente por tipo — nenhuma declaração adicional é necessária.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro | Constante existente | Condição | Status HTTP |
|---|---|---|---|
| Produto soft-deletado | `ProductError.CANNOT_MODIFY_DELETED_PRODUCT` | `product.isDeleted() == true` | `422` |
| Delta zero | `StockMovementError.QUANTITY_ZERO` | `command.delta() == 0` | `422` |
| Tipo de movimentação nulo/inválido | `StockMovementError.MOVEMENT_TYPE_NULL` | `movementType` nulo, vazio ou não pertence ao enum | `422` |
| Estoque insuficiente | `StockMovementError.INSUFFICIENT_STOCK` | `delta < 0` e `product.quantity < |delta|` | `422` |
| Produto não encontrado | `NotFoundException` (lançada diretamente) | `productGateway.findById()` retorna `Optional.empty()` | `404` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | Outra transação alterou o produto no intervalo | `409` |

> Todos os erros de validação (QUANTITY_ZERO, MOVEMENT_TYPE_NULL, INSUFFICIENT_STOCK) são acumulados no `Notification` **antes** de entrar na transação. Apenas o `CANNOT_MODIFY_DELETED_PRODUCT` é verificado logo após o carregamento do produto.

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products/{productId}/stock/adjustments`

```json
{
  "delta": -5,
  "movementType": "ADJUSTMENT",
  "notes": "Baixa por avaria detectada na contagem física de abril/2026",
  "referenceId": "01965f3a-0000-7000-0000-000000000050",
  "referenceType": "CONTAGEM_FISICA"
}
```

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `delta` | `int` | Sim | ≠ 0; positivo = entrada, negativo = saída |
| `movementType` | `string` | Sim | `IN`, `OUT`, `ADJUSTMENT`, `RETURN`, `RESERVE`, `RELEASE` |
| `notes` | `string` | Não | Máx. 1000 caracteres |
| `referenceId` | `string (UUID)` | Não | UUID válido ou ausente |
| `referenceType` | `string` | Não | Máx. 50 caracteres |

### Response (Sucesso — 201 Created)

```json
{
  "movementId": "01965f3a-0000-7000-0000-000000000060",
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "movementType": "ADJUSTMENT",
  "delta": -5,
  "quantityAfter": 12,
  "productStatus": "ACTIVE",
  "createdAt": "2026-04-11T14:30:00Z"
}
```

> Quando a saída zera o estoque: `quantityAfter = 0`, `productStatus = "OUT_OF_STOCK"`.
> Quando uma entrada repõe estoque de produto `OUT_OF_STOCK`: `productStatus = "ACTIVE"`.

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Estoque insuficiente para realizar a operação"],
  "timestamp": "2026-04-11T14:30:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/stock/adjustments"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **`StockAdjustedEvent`** — criar em `domain/.../catalog/events/`; verificar o padrão exato do construtor `super(eventType, aggregateType)` em outros eventos do catálogo.
2. **`AdjustStockCommand`** — record com os 6 campos.
3. **`AdjustStockOutput`** — record com factory `from(Product, StockMovement)`.
4. **`AdjustStockUseCase`** — lógica com `Notification` + `transactionManager.execute()`; importar `ProductId`, `StockMovement`, `StockMovementType`.
5. **Verificar `ProductJpaEntity.updateFrom()`** — confirmar que `quantity` e `status` são copiados do aggregate para a JPA entity (devem estar — mas vale checar antes de testar).
6. **`@Bean adjustStockUseCase`** em `UseCaseConfig`.
7. **`AdjustStockRequest`** — record com `@NotNull` em `delta` e `@NotBlank` em `movementType`.
8. **`AdjustStockResponse`** — record simples com factory.
9. **Endpoint** `POST /{productId}/stock/adjustments` no `ProductController`.
10. **Testes unitários** — `AdjustStockUseCase` com Mockito:
    - delta positivo → `addStock()` chamado, movement gravado, status ACTIVE mantido
    - delta negativo com estoque suficiente → `deductStock()` chamado, status pode mudar para OUT_OF_STOCK
    - delta negativo com estoque insuficiente → `Left(notification)` com `INSUFFICIENT_STOCK`
    - delta zero → `Left(notification)` com `QUANTITY_ZERO`
    - produto deletado → `Left(notification)` com `CANNOT_MODIFY_DELETED_PRODUCT`
    - produto não encontrado → `NotFoundException` propagada (404)
    - movementType inválido → `Left(notification)` com `MOVEMENT_TYPE_NULL`
11. **Testes de integração** (`AdjustStockIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real; verificar que:
    - `catalog.stock_movements` recebe a linha na partição correta
    - `catalog.products.quantity` é atualizado atomicamente
    - falha em `stockMovementGateway.save()` faz rollback do `productGateway.update()` (teste de atomicidade)
