# Task: UC-60 — PauseProduct

## 📋 Resumo
Permite pausar temporariamente um produto ativo, transitando seu status de `ACTIVE` (ou `OUT_OF_STOCK`) para `INACTIVE`. Um produto pausado permanece cadastrado no sistema mas não aparece como disponível para venda no catálogo. A operação registra um `ProductPausedEvent` para rastreabilidade e notificação de sistemas downstream (ex: remoção de índice de busca).

## 🎯 Objetivo
Ao receber um `POST /api/v1/catalog/products/{id}/pause`, o sistema deve:
1. Localizar o produto pelo ID (404 se não encontrado ou deletado);
2. Verificar pré-condições via regras de domínio acumuladas em `Notification`;
3. Transitar o status para `INACTIVE` via método de ciclo de vida no aggregate;
4. Persistir via `gateway.update()` dentro de uma transação;
5. Retornar os dados atualizados com status `200 OK`.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (lógica), `domain` (ciclo de vida), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `POST /api/v1/catalog/products/{id}/pause`
* **Tabelas do Banco:** `catalog.products`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/entity/Product.java` — refatorar `pause()` para receber `Notification`, validar pré-condições e retornar sem mutar estado se houver erros.
2. **ALTERAR** `domain/.../catalog/error/ProductError.java` — adicionar `PRODUCT_ALREADY_INACTIVE` e `CANNOT_PAUSE_IN_CURRENT_STATUS`.

### `application`
1. **CRIAR** `application/.../catalog/product/PauseProductCommand.java` — record com apenas `id`.
2. **CRIAR** `application/.../catalog/product/PauseProductOutput.java` — snapshot completo do produto com factory `from(Product)`.
3. **CRIAR** `application/.../catalog/product/PauseProductUseCase.java` — lógica principal com `Either`.

### `infrastructure`
Nenhum arquivo novo — `findById()` e `update()` já existem em `ProductPostgresGateway`.

### `api`
1. **CRIAR** `api/.../catalog/PauseProductResponse.java` — record com factory `from(PauseProductOutput)`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `POST /{id}/pause` (sem request body).
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `PauseProductUseCase`.

> Nenhum `Request` DTO é necessário — a ação é identificada pela URL, sem corpo.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Ciclo de Vida no Aggregate (Domain)

Refatorar `Product.pause()` para receber `Notification` e acumular erros sem lançar exceções:

```java
public void pause(final Notification notification) {
    if (this.deletedAt != null) {
        notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
        return;
    }
    if (ProductStatus.INACTIVE.equals(this.status)) {
        notification.append(ProductError.PRODUCT_ALREADY_INACTIVE);
        return;
    }
    if (!ProductStatus.ACTIVE.equals(this.status)
            && !ProductStatus.OUT_OF_STOCK.equals(this.status)) {
        notification.append(ProductError.CANNOT_PAUSE_IN_CURRENT_STATUS);
        return;
    }
    this.status    = ProductStatus.INACTIVE;
    this.updatedAt = Instant.now();
    registerEvent(new ProductPausedEvent(getId().getValue().toString()));
}
```

> Transições válidas: `ACTIVE → INACTIVE` e `OUT_OF_STOCK → INACTIVE`.
> Transições inválidas: `DRAFT` e `DISCONTINUED` — produto precisa estar publicado para ser pausado.
> `incrementVersion()` removido — o JPA gerencia a versão automaticamente no `save()`.

**Erros em `ProductError`** — adicionar:
```java
public static final Error PRODUCT_ALREADY_INACTIVE =
        new Error("Produto já está inativo.");

public static final Error CANNOT_PAUSE_IN_CURRENT_STATUS =
        new Error("Produto não pode ser pausado no status atual. Apenas produtos ACTIVE ou OUT_OF_STOCK podem ser pausados.");
```

> `ProductPausedEvent` já existe — não precisa ser criado.

### 2. Contrato de Entrada/Saída (Application)

**`PauseProductCommand`**:
```java
public record PauseProductCommand(String id) {}
```

**`PauseProductOutput`**: mesmos campos de `PublishProductOutput` / `UpdateProductOutput` — snapshot completo. Factory `from(Product)`.

### 3. Lógica do Use Case (Application)

```java
@Override
public Either<Notification, PauseProductOutput> execute(final PauseProductCommand command) {
    final var notification = Notification.create();

    // 1. Carregar produto (NotFoundException = pré-condição, fora do Either)
    final var product = productGateway.findById(ProductId.from(command.id()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    // 2. Aplicar transição de status — pré-condições acumuladas em Notification
    product.pause(notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    // 3. Persistir e publicar eventos dentro da transação
    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return PauseProductOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

> As validações de idempotência e status inválido ficam dentro do próprio `product.pause(notification)` — não é necessário checá-las separadamente no use case.

### 4. Persistência (Infrastructure)

Nenhuma mudança necessária. `ProductPostgresGateway.update()` carrega a `ProductJpaEntity` pelo ID, chama `updateFrom(aggregate)` e persiste — preservando `id` e `version`.

Confirmar que `ProductJpaEntity.updateFrom(Product)` mapeia o campo `status`:
```java
public void updateFrom(final Product aggregate) {
    // ...
    this.status    = aggregate.getStatus();   // ← obrigatório
    this.updatedAt = aggregate.getUpdatedAt();
    // ...
}
```

### 5. Roteamento e Injeção (API)

**`PauseProductResponse`**: record com os mesmos campos de `PublishProductResponse`. Factory `from(PauseProductOutput)`.

**Endpoint no `ProductController`**:
```java
@PostMapping("/{id}/pause")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Pausar produto",
        description = "Transita o produto de ACTIVE (ou OUT_OF_STOCK) para INACTIVE, removendo-o temporariamente do catálogo.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Produto pausado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Regras de negócio violadas (já inativo, status inválido, deletado)")
})
public PauseProductResponse pause(@PathVariable final String id) {
    return PauseProductResponse.from(
            pauseProductUseCase.execute(new PauseProductCommand(id))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> Sem `@RequestBody` nem `@Valid` — a ação é identificada pelo path `/{id}/pause`.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public PauseProductUseCase pauseProductUseCase(
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new PauseProductUseCase(productGateway, eventPublisher, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.PRODUCT_NOT_FOUND` | ID não existe ou produto deletado | `404 Not Found` |
| `ProductError.CANNOT_PUBLISH_DELETED_PRODUCT` | `deletedAt` não é nulo | `422 Unprocessable Entity` |
| `ProductError.PRODUCT_ALREADY_INACTIVE` | Produto já está com status `INACTIVE` | `422 Unprocessable Entity` |
| `ProductError.CANNOT_PAUSE_IN_CURRENT_STATUS` | Status é `DRAFT` ou `DISCONTINUED` | `422 Unprocessable Entity` |
| `ObjectOptimisticLockingFailureException` | Versão desatualizada (concorrência) | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products/{id}/pause`
Sem corpo. A intenção é expressa pelo path.

### Response (Sucesso — 200 OK)
```json
{
  "id": "01965f3a-0000-7000-0000-000000000010",
  "categoryId": "01965f3a-0000-7000-0000-000000000001",
  "brandId": "01965f3a-0000-7000-0000-000000000002",
  "name": "Tênis Running Pro X2",
  "slug": "tenis-running-pro-x2",
  "description": "Tênis de alta performance para corridas de longa distância.",
  "shortDescription": "Tênis running leve e responsivo.",
  "sku": "TEN-RUN-X2-42",
  "price": 499.90,
  "compareAtPrice": 599.90,
  "costPrice": 220.00,
  "status": "INACTIVE",
  "featured": true,
  "quantity": 10,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "createdAt": "2026-04-10T00:00:00Z",
  "updatedAt": "2026-04-10T22:30:00Z",
  "images": []
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Produto não encontrado"],
  "timestamp": "2026-04-10T22:30:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000099/pause"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Produto não pode ser pausado no status atual. Apenas produtos ACTIVE ou OUT_OF_STOCK podem ser pausados."],
  "timestamp": "2026-04-10T22:30:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/pause"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Adicionar em `ProductError`** — `PRODUCT_ALREADY_INACTIVE` e `CANNOT_PAUSE_IN_CURRENT_STATUS`.
2. **Refatorar `Product.pause(Notification)`** — validar deletado, já inativo e status inválido; acumular erros sem lançar exceção; registrar `ProductPausedEvent` apenas se sem erros. (`ProductPausedEvent` já existe.)
3. **`PauseProductCommand`** — record com apenas `id`.
4. **`PauseProductOutput`** — snapshot completo com factory `from(Product)`.
5. **`PauseProductUseCase`** — lógica com `Either`; delega todas as pré-condições para `product.pause(notification)`.
6. **`@Bean` em `UseCaseConfig`** — wiring do `PauseProductUseCase`.
7. **`PauseProductResponse`** — record com factory `from(PauseProductOutput)`.
8. **Endpoint `POST /{id}/pause`** no `ProductController` sem request body.
9. **Testes unitários** — `Product.pause()` (domain): ACTIVE válido, OUT_OF_STOCK válido, DRAFT inválido, DISCONTINUED inválido, já INACTIVE, produto deletado. `PauseProductUseCase` (application) com Mockito.
10. **Testes de integração** (`PauseProductIT.java` em infrastructure) — Testcontainers + PostgreSQL real; verificar `status = 'INACTIVE'` na tabela após pause.
