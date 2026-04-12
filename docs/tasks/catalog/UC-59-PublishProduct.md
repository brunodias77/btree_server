# Task: UC-59 — PublishProduct

## 📋 Resumo
Realiza a transição de status do produto de `DRAFT` para `ACTIVE`, tornando-o visível e disponível para venda no catálogo. A publicação exige que o produto possua preço definido e não esteja deletado (soft-delete). A operação registra um `ProductPublishedEvent` para notificação de sistemas downstream (ex: indexação de busca, feed de catálogo).

## 🎯 Objetivo
Ao receber um `POST /api/v1/catalog/products/{id}/publish`, o sistema deve:
1. Localizar o produto pelo ID (404 se não encontrado ou deletado);
2. Verificar pré-condições de publicação via regras de domínio acumuladas em `Notification`;
3. Transitar o status para `ACTIVE` via método de ciclo de vida no aggregate;
4. Persistir via `gateway.update()` dentro de uma transação;
5. Retornar os dados atualizados com status `200 OK`.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (lógica), `domain` (ciclo de vida), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `POST /api/v1/catalog/products/{id}/publish`
* **Tabelas do Banco:** `catalog.products`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **VERIFICAR / ALTERAR** `domain/.../catalog/entity/Product.java` — confirmar que o método `publish()` existe e acumula erros em `Notification`; ajustar se lançar exceção diretamente.
2. **CRIAR** `domain/.../catalog/events/ProductPublishedEvent.java` — Domain Event disparado após publicação bem-sucedida.
3. **VERIFICAR** `domain/.../catalog/error/ProductError.java` — confirmar presença de `CANNOT_PUBLISH_WITHOUT_PRICE` e `CANNOT_PUBLISH_DELETED_PRODUCT`; adicionar se ausentes.

### `application`
1. **CRIAR** `application/.../catalog/product/PublishProductCommand.java` — record com apenas `id`.
2. **CRIAR** `application/.../catalog/product/PublishProductOutput.java` — record com snapshot completo do produto após publicação, com factory `from(Product)`.
3. **CRIAR** `application/.../catalog/product/PublishProductUseCase.java` — lógica principal com `Either`.

### `infrastructure`
Nenhum arquivo novo — `ProductJpaRepository` e `ProductPostgresGateway` já possuem `findById()` e `update()` suficientes.

### `api`
1. **CRIAR** `api/.../catalog/PublishProductResponse.java` — record com snapshot do produto publicado, com factory `from(PublishProductOutput)`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `POST /{id}/publish` (sem request body).
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `PublishProductUseCase`.

> Nenhum `Request` DTO é necessário — a ação é identificada pela URL, sem corpo.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Ciclo de Vida no Aggregate (Domain)

O método `publish()` em `Product.java` deve acumular erros em `Notification` **sem lançar exceções**:

```java
public void publish(final Notification notification) {
    if (this.deletedAt != null) {
        notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
    }

    if (this.price == null || this.price.compareTo(BigDecimal.ZERO) < 0) {
        notification.append(ProductError.CANNOT_PUBLISH_WITHOUT_PRICE);
    }

    if (notification.hasError()) {
        return;
    }

    this.status    = ProductStatus.ACTIVE;
    this.updatedAt = Instant.now();

    registerEvent(new ProductPublishedEvent(this.id.getValue()));
}
```

> Se o método atual lança `BusinessRuleException` ou `DomainException`, refatorar para receber `Notification` e acumular — seguindo o mesmo padrão do `create()`.

**`ProductPublishedEvent`**:
```java
public class ProductPublishedEvent extends DomainEvent {
    private final UUID productId;

    public ProductPublishedEvent(final UUID productId) {
        super();
        this.productId = productId;
    }

    public UUID getProductId() { return productId; }
}
```

**Erros em `ProductError`** — confirmar/adicionar:
```java
public static final Error CANNOT_PUBLISH_WITHOUT_PRICE =
        new Error("Produto deve ter preço definido para ser publicado.");

public static final Error CANNOT_PUBLISH_DELETED_PRODUCT =
        new Error("Produto deletado não pode ser publicado.");

public static final Error PRODUCT_ALREADY_ACTIVE =
        new Error("Produto já está ativo.");
```

> `PRODUCT_ALREADY_ACTIVE` é opcional — depende da decisão de negócio: idempotência (ignorar silenciosamente) ou erro explícito. Recomendar erro explícito para clareza.

### 2. Contrato de Entrada/Saída (Application)

**`PublishProductCommand`**:
```java
public record PublishProductCommand(String id) {}
```

**`PublishProductOutput`**: mesmo conjunto de campos do `CreateProductOutput` / `UpdateProductOutput` — snapshot completo do produto. Criar com factory `from(Product)` idêntica às anteriores para consistência da API.

### 3. Lógica do Use Case (Application)

```java
@Override
public Either<Notification, PublishProductOutput> execute(final PublishProductCommand command) {
    final var notification = Notification.create();

    // 1. Carregar produto (NotFoundException fora do Either — erro de pré-condição, não acumulável)
    final var product = productGateway.findById(ProductId.from(command.id()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    // 2. Verificar idempotência — produto já ativo
    if (ProductStatus.ACTIVE.equals(product.getStatus())) {
        notification.append(ProductError.PRODUCT_ALREADY_ACTIVE);
        return Left(notification);
    }

    // 3. Aplicar transição de status (acumula erros de regra de negócio no Notification)
    product.publish(notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    // 4. Persistir e publicar eventos dentro da transação
    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return PublishProductOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

> O `Try().toEither()` captura falhas de infraestrutura (ex: `OptimisticLockException`) e converte em `Left(Notification)` — o `GlobalExceptionHandler` trata `ObjectOptimisticLockingFailureException` com HTTP 409.

### 4. Persistência (Infrastructure)

Nenhuma mudança necessária. O `ProductPostgresGateway.update()` já carrega a JpaEntity pelo ID, chama `updateFrom(aggregate)` e salva — preservando `id` e `version` para o optimistic locking.

Confirmar que `ProductJpaEntity.updateFrom(Product)` mapeia o campo `status` corretamente:
```java
public void updateFrom(final Product aggregate) {
    // ...
    this.status    = aggregate.getStatus();   // ← deve estar presente
    this.updatedAt = aggregate.getUpdatedAt();
    // ...
}
```

### 5. Roteamento e Injeção (API)

**`PublishProductResponse`**: record idêntico ao `CreateProductResponse` / `UpdateProductResponse` — mesmo snapshot de campos. Factory `from(PublishProductOutput)`.

**Endpoint no `ProductController`**:
```java
@PostMapping("/{id}/publish")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Publicar produto",
        description = "Transita o produto de DRAFT para ACTIVE, tornando-o visível no catálogo.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Produto publicado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Regras de negócio violadas (sem preço, já deletado, já ativo)")
})
public PublishProductResponse publish(@PathVariable final String id) {
    final var command = new PublishProductCommand(id);
    return PublishProductResponse.from(
            publishProductUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> Sem `@RequestBody` nem `@Valid` — a ação é completamente identificada pelo path `/{id}/publish`.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public PublishProductUseCase publishProductUseCase(
        final ProductGateway productGateway,
        final TransactionManager transactionManager,
        final DomainEventPublisher eventPublisher
) {
    return new PublishProductUseCase(productGateway, transactionManager, eventPublisher);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.PRODUCT_NOT_FOUND` | ID não existe ou produto deletado | `404 Not Found` |
| `ProductError.PRODUCT_ALREADY_ACTIVE` | Produto já está com status `ACTIVE` | `422 Unprocessable Entity` |
| `ProductError.CANNOT_PUBLISH_WITHOUT_PRICE` | `price` é nulo ou negativo | `422 Unprocessable Entity` |
| `ProductError.CANNOT_PUBLISH_DELETED_PRODUCT` | `deletedAt` não é nulo | `422 Unprocessable Entity` |
| `ObjectOptimisticLockingFailureException` | Versão desatualizada (concorrência) | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products/{id}/publish`
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
  "status": "ACTIVE",
  "featured": true,
  "quantity": 0,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "createdAt": "2026-04-10T00:00:00Z",
  "updatedAt": "2026-04-10T22:15:00Z",
  "images": []
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Produto não encontrado"],
  "timestamp": "2026-04-10T22:15:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000099/publish"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Produto deve ter preço definido para ser publicado."],
  "timestamp": "2026-04-10T22:15:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/publish"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Verificar `ProductError`** — confirmar/adicionar `CANNOT_PUBLISH_WITHOUT_PRICE`, `CANNOT_PUBLISH_DELETED_PRODUCT` e `PRODUCT_ALREADY_ACTIVE`.
2. **`ProductPublishedEvent`** — Domain Event simples com `productId`.
3. **Refatorar/verificar `Product.publish(Notification)`** — garantir acumulação de erros sem lançar exceção; registrar `ProductPublishedEvent` ao final.
4. **`PublishProductCommand`** — record com apenas `id`.
5. **`PublishProductOutput`** — record com snapshot completo e factory `from(Product)`.
6. **`PublishProductUseCase`** — lógica com `Either`, guarda de idempotência, delegação para `product.publish(notification)`.
7. **`@Bean` em `UseCaseConfig`** — wiring do `PublishProductUseCase`.
8. **`PublishProductResponse`** — record com factory `from(PublishProductOutput)`.
9. **Endpoint `POST /{id}/publish`** no `ProductController` sem request body.
10. **Testes unitários** — `Product.publish()` (domain): cenários DRAFT sem preço, DRAFT com preço, produto deletado, produto já ativo. `PublishProductUseCase` (application) com Mockito.
11. **Testes de integração** (`PublishProductIT.java` em infrastructure) — Testcontainers + PostgreSQL real, verificar `status = 'ACTIVE'` na tabela após publicação.
