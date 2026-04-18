# Task: UC-61 — ArchiveProduct

## 📋 Resumo
Permite descontinuar permanentemente um produto, transitando seu status para `DISCONTINUED`. Um produto arquivado não pode ser reativado pelo fluxo normal — representa o fim do ciclo de vida no catálogo. A operação não realiza soft-delete físico (campo `deleted_at`); o produto permanece consultável para fins de histórico de pedidos e auditoria. Registra um `ProductArchivedEvent` para notificação de sistemas downstream (ex: remoção definitiva do índice de busca, desativação de promoções vinculadas).

## 🎯 Objetivo
Ao receber um `POST /api/v1/catalog/products/{id}/archive`, o sistema deve:
1. Localizar o produto pelo ID (404 se não encontrado ou já deletado com `deleted_at`);
2. Verificar pré-condições de arquivamento via regras de domínio acumuladas em `Notification`;
3. Transitar o status para `DISCONTINUED` via método de ciclo de vida no aggregate;
4. Persistir via `gateway.update()` dentro de uma transação;
5. Retornar os dados atualizados com status `200 OK`.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (lógica), `domain` (ciclo de vida), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `POST /api/v1/catalog/products/{id}/archive`
* **Tabelas do Banco:** `catalog.products`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/entity/Product.java` — refatorar `archive()` para receber `Notification`, validar pré-condições e retornar sem mutar estado se houver erros.
2. **ALTERAR** `domain/.../catalog/error/ProductError.java` — adicionar `PRODUCT_ALREADY_DISCONTINUED` e `CANNOT_ARCHIVE_IN_CURRENT_STATUS`.

### `application`
1. **CRIAR** `application/.../catalog/product/ArchiveProductCommand.java` — record com apenas `id`.
2. **CRIAR** `application/.../catalog/product/ArchiveProductOutput.java` — snapshot completo do produto com factory `from(Product)`.
3. **CRIAR** `application/.../catalog/product/ArchiveProductUseCase.java` — lógica principal com `Either`.

### `infrastructure`
Nenhum arquivo novo — `findById()` e `update()` já existem em `ProductPostgresGateway`.

### `api`
1. **CRIAR** `api/.../catalog/ArchiveProductResponse.java` — record com factory `from(ArchiveProductOutput)`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `POST /{id}/archive` (sem request body).
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `ArchiveProductUseCase`.

> Nenhum `Request` DTO é necessário — a ação é identificada pela URL, sem corpo.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Ciclo de Vida no Aggregate (Domain)

Refatorar `Product.archive()` para receber `Notification` e acumular erros sem lançar exceções:

```java
public void archive(final Notification notification) {
    if (this.deletedAt != null) {
        notification.append(ProductError.CANNOT_PUBLISH_DELETED_PRODUCT);
        return;
    }
    if (ProductStatus.DISCONTINUED.equals(this.status)) {
        notification.append(ProductError.PRODUCT_ALREADY_DISCONTINUED);
        return;
    }
    if (ProductStatus.DRAFT.equals(this.status)) {
        notification.append(ProductError.CANNOT_ARCHIVE_IN_CURRENT_STATUS);
        return;
    }
    this.status    = ProductStatus.DISCONTINUED;
    this.updatedAt = Instant.now();
    registerEvent(new ProductArchivedEvent(getId().getValue().toString(), this.sku));
}
```

> Transições válidas: `ACTIVE → DISCONTINUED`, `INACTIVE → DISCONTINUED`, `OUT_OF_STOCK → DISCONTINUED`.
> Transição inválida: `DRAFT` — produto nunca publicado não pode ser arquivado; use soft-delete (`DeleteProduct`) para descartá-lo.
> `incrementVersion()` removido — o JPA gerencia a versão automaticamente no `save()`.

**Erros em `ProductError`** — adicionar:
```java
public static final Error PRODUCT_ALREADY_DISCONTINUED =
        new Error("Produto já está descontinuado.");

public static final Error CANNOT_ARCHIVE_IN_CURRENT_STATUS =
        new Error("Produto em DRAFT não pode ser arquivado. Apenas produtos publicados (ACTIVE, INACTIVE, OUT_OF_STOCK) podem ser descontinuados.");
```

> `ProductArchivedEvent` já existe — não precisa ser criado.

### 2. Contrato de Entrada/Saída (Application)

**`ArchiveProductCommand`**:
```java
public record ArchiveProductCommand(String id) {}
```

**`ArchiveProductOutput`**: mesmos campos de `PublishProductOutput` / `PauseProductOutput` — snapshot completo. Factory `from(Product)`.

### 3. Lógica do Use Case (Application)

```java
@Override
public Either<Notification, ArchiveProductOutput> execute(final ArchiveProductCommand command) {
    final var notification = Notification.create();

    // 1. Carregar produto (NotFoundException = pré-condição, fora do Either)
    final var product = productGateway.findById(ProductId.from(command.id()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    // 2. Aplicar transição de status — pré-condições acumuladas em Notification
    product.archive(notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    // 3. Persistir e publicar eventos dentro da transação
    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return ArchiveProductOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

> Toda a lógica de validação fica dentro do `product.archive(notification)` — o use case apenas delega e verifica o resultado.

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

**`ArchiveProductResponse`**: record com os mesmos campos de `PublishProductResponse` / `PauseProductResponse`. Factory `from(ArchiveProductOutput)`.

**Endpoint no `ProductController`**:
```java
@PostMapping("/{id}/archive")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Arquivar produto",
        description = "Descontinua o produto transitando para DISCONTINUED. Irreversível pelo fluxo normal — produto mantido para histórico.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Produto arquivado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Regras de negócio violadas (já descontinuado, DRAFT, deletado)")
})
public ArchiveProductResponse archive(@PathVariable final String id) {
    return ArchiveProductResponse.from(
            archiveProductUseCase.execute(new ArchiveProductCommand(id))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> Sem `@RequestBody` nem `@Valid` — a ação é identificada pelo path `/{id}/archive`.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ArchiveProductUseCase archiveProductUseCase(
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new ArchiveProductUseCase(productGateway, eventPublisher, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.PRODUCT_NOT_FOUND` | ID não existe ou produto com `deleted_at` preenchido | `404 Not Found` |
| `ProductError.CANNOT_PUBLISH_DELETED_PRODUCT` | `deletedAt` não é nulo | `422 Unprocessable Entity` |
| `ProductError.PRODUCT_ALREADY_DISCONTINUED` | Produto já está `DISCONTINUED` | `422 Unprocessable Entity` |
| `ProductError.CANNOT_ARCHIVE_IN_CURRENT_STATUS` | Status é `DRAFT` | `422 Unprocessable Entity` |
| `ObjectOptimisticLockingFailureException` | Versão desatualizada (concorrência) | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products/{id}/archive`
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
  "status": "DISCONTINUED",
  "featured": false,
  "quantity": 0,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "createdAt": "2026-04-10T00:00:00Z",
  "updatedAt": "2026-04-10T23:00:00Z",
  "images": []
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Produto não encontrado"],
  "timestamp": "2026-04-10T23:00:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000099/archive"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Produto em DRAFT não pode ser arquivado. Apenas produtos publicados (ACTIVE, INACTIVE, OUT_OF_STOCK) podem ser descontinuados."],
  "timestamp": "2026-04-10T23:00:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/archive"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Adicionar em `ProductError`** — `PRODUCT_ALREADY_DISCONTINUED` e `CANNOT_ARCHIVE_IN_CURRENT_STATUS`.
2. **Refatorar `Product.archive(Notification)`** — validar deletado, já descontinuado e status DRAFT; acumular erros sem lançar exceção; registrar `ProductArchivedEvent` apenas se sem erros. (`ProductArchivedEvent` já existe.)
3. **`ArchiveProductCommand`** — record com apenas `id`.
4. **`ArchiveProductOutput`** — snapshot completo com factory `from(Product)`.
5. **`ArchiveProductUseCase`** — lógica com `Either`; delega todas as pré-condições para `product.archive(notification)`.
6. **`@Bean` em `UseCaseConfig`** — wiring do `ArchiveProductUseCase`.
7. **`ArchiveProductResponse`** — record com factory `from(ArchiveProductOutput)`.
8. **Endpoint `POST /{id}/archive`** no `ProductController` sem request body.
9. **Testes unitários** — `Product.archive()` (domain): ACTIVE válido, INACTIVE válido, OUT_OF_STOCK válido, DRAFT inválido, já DISCONTINUED, produto deletado. `ArchiveProductUseCase` (application) com Mockito.
10. **Testes de integração** (`ArchiveProductIT.java` em infrastructure) — Testcontainers + PostgreSQL real; verificar `status = 'DISCONTINUED'` na tabela após archive.
