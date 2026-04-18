# Task: UC-58 — UpdateProduct

## 📋 Resumo
Permite editar os dados cadastrais de um produto existente (nome, slug, SKU, preço, descrição, dimensões, categoria, marca, etc.). O produto deve existir e não estar deletado (soft-delete). Alterações de slug e SKU passam por verificação de unicidade excluindo o próprio produto. A operação registra um `ProductUpdatedEvent` para rastreabilidade e notificação de sistemas downstream.

## 🎯 Objetivo
Ao receber um `PATCH /api/v1/catalog/products/{id}`, o sistema deve:
1. Localizar o produto pelo ID (404 se não encontrado ou deletado);
2. Validar unicidade de `slug` e `SKU` novos (excluindo o próprio produto);
3. Aplicar as alterações via método de mutação no aggregate;
4. Persistir via `gateway.update()` dentro de uma transação;
5. Retornar os dados atualizados com status `200 OK`.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (lógica), `domain` (mutação), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `PATCH /api/v1/catalog/products/{id}`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/entity/Product.java` — adicionar método de mutação `update(...)` que valida via `Notification` e registra `ProductUpdatedEvent`.
2. **CRIAR** `domain/.../catalog/events/ProductUpdatedEvent.java` — Domain Event disparado após edição bem-sucedida.
3. **ALTERAR** `domain/.../catalog/gateway/ProductGateway.java` — adicionar `existsBySlugExcludingId(String slug, ProductId id)` e `existsBySkuExcludingId(String sku, ProductId id)`.

### `application`
1. **CRIAR** `application/.../catalog/product/UpdateProductCommand.java` — record com `id` + campos editáveis.
2. **CRIAR** `application/.../catalog/product/UpdateProductOutput.java` — record idêntico ao `CreateProductOutput` com factory `from(Product)`.
3. **CRIAR** `application/.../catalog/product/UpdateProductUseCase.java` — lógica principal com `Either`.

### `infrastructure`
1. **ALTERAR** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — adicionar `existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id)` e `existsBySkuAndIdNotAndDeletedAtIsNull(String sku, UUID id)`.
2. **ALTERAR** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — implementar os dois novos métodos do gateway.

### `api`
1. **CRIAR** `api/.../catalog/UpdateProductRequest.java` — record com Bean Validation.
2. **CRIAR** `api/.../catalog/UpdateProductResponse.java` — record com factory `from(UpdateProductOutput)`.
3. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `PATCH /{id}`.
4. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `UpdateProductUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Mutação no Aggregate (Domain)

Adicionar em `Product.java` o método `update(...)` que recebe os campos editáveis e um `Notification`:

```java
public void update(
        final CategoryId categoryId,
        final BrandId brandId,
        final String name,
        final String slug,
        final String description,
        final String shortDescription,
        final String sku,
        final BigDecimal price,
        final BigDecimal compareAtPrice,
        final BigDecimal costPrice,
        final Integer lowStockThreshold,
        final ProductDimensions dimensions,
        final boolean featured,
        final Notification notification
) {
    this.categoryId     = categoryId;
    this.brandId        = brandId;
    this.name           = name;
    this.slug           = slug;
    this.description    = description;
    this.shortDescription = shortDescription;
    this.sku            = sku;
    this.price          = price;
    this.compareAtPrice = compareAtPrice;
    this.costPrice      = costPrice;
    this.lowStockThreshold = lowStockThreshold != null ? lowStockThreshold : 0;
    this.dimensions     = dimensions != null ? dimensions : ProductDimensions.empty();
    this.featured       = featured;
    this.updatedAt      = Instant.now();

    new ProductValidator(this, notification).validate();

    if (!notification.hasError()) {
        registerEvent(new ProductUpdatedEvent(this.id.getValue()));
    }
}
```

> `update()` nunca altera `id`, `status`, `quantity`, `createdAt` ou `deletedAt` — esses campos têm seus próprios métodos de ciclo de vida.

**`ProductUpdatedEvent`**:
```java
public class ProductUpdatedEvent extends DomainEvent {
    private final UUID productId;

    public ProductUpdatedEvent(final UUID productId) {
        super();
        this.productId = productId;
    }

    public UUID getProductId() { return productId; }
}
```

**Novos métodos no `ProductGateway`**:
```java
boolean existsBySlugExcludingId(String slug, ProductId id);
boolean existsBySkuExcludingId(String sku, ProductId id);
```

### 2. Contrato de Entrada/Saída (Application)

**`UpdateProductCommand`**:
```java
public record UpdateProductCommand(
        String id,
        String categoryId,
        String brandId,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        Integer lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        boolean featured
) {}
```

> Sem lista de imagens — gerenciamento de imagens pertence a use cases dedicados (ex: `AddProductImage`, `RemoveProductImage`).

**`UpdateProductOutput`**: idêntico ao `CreateProductOutput` — mesmo record, mesma factory `from(Product)`. Pode ser criado como alias ou duplicado explicitamente para evitar acoplamento entre use cases.

### 3. Lógica do Use Case (Application)

```java
@Override
public Either<Notification, UpdateProductOutput> execute(final UpdateProductCommand command) {
    final var notification = Notification.create();

    // 1. Carregar produto
    final var productId = ProductId.from(command.id());
    final var product = productGateway.findById(productId)
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    // 2. Verificar unicidade de slug (excluindo o próprio produto)
    if (!product.getSlug().equals(command.slug())
            && productGateway.existsBySlugExcludingId(command.slug(), productId)) {
        notification.append(ProductError.SLUG_ALREADY_EXISTS);
    }

    // 3. Verificar unicidade de SKU (excluindo o próprio produto)
    if (!product.getSku().equals(command.sku())
            && productGateway.existsBySkuExcludingId(command.sku(), productId)) {
        notification.append(ProductError.SKU_ALREADY_EXISTS);
    }

    if (notification.hasError()) {
        return Left(notification);
    }

    // 4. Resolver IDs opcionais
    final var categoryId = command.categoryId() != null
            ? CategoryId.from(command.categoryId()) : null;
    final var brandId = command.brandId() != null
            ? BrandId.from(command.brandId()) : null;

    // 5. Construir dimensões
    final var dimensions = (command.weight() != null || command.width() != null
            || command.height() != null || command.depth() != null)
            ? ProductDimensions.of(command.weight(), command.width(), command.height(), command.depth())
            : ProductDimensions.empty();

    // 6. Aplicar mutação no aggregate (acumula erros no mesmo Notification)
    product.update(
            categoryId, brandId,
            command.name(), command.slug(),
            command.description(), command.shortDescription(),
            command.sku(), command.price(),
            command.compareAtPrice(), command.costPrice(),
            command.lowStockThreshold(), dimensions,
            command.featured(), notification
    );

    if (notification.hasError()) {
        return Left(notification);
    }

    // 7. Persistir e publicar eventos dentro da transação
    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return UpdateProductOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

### 4. Persistência (Infrastructure)

**Novos métodos no `ProductJpaRepository`**:
```java
boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);
boolean existsBySkuAndIdNotAndDeletedAtIsNull(String sku, UUID id);
```

**Implementação no `ProductPostgresGateway`**:
```java
@Override
@Transactional(readOnly = true)
public boolean existsBySlugExcludingId(final String slug, final ProductId id) {
    return productRepository.existsBySlugAndIdNotAndDeletedAtIsNull(slug, id.getValue());
}

@Override
@Transactional(readOnly = true)
public boolean existsBySkuExcludingId(final String sku, final ProductId id) {
    return productRepository.existsBySkuAndIdNotAndDeletedAtIsNull(sku, id.getValue());
}
```

> O método `update()` já existe no `ProductPostgresGateway` (implementado para UC-57). Verificar se chama `updateFrom()` na JpaEntity preservando `id` e `version`.

A `ProductJpaEntity.updateFrom(Product)` já deve lidar com a sincronização de imagens (mantendo IDs existentes, adicionando novas, removendo ausentes via `orphanRemoval = true`). Confirmar que está implementada corretamente.

### 5. Roteamento e Injeção (API)

**`UpdateProductRequest`**:
```java
public record UpdateProductRequest(
        @Schema(description = "ID da categoria (opcional)")
        String categoryId,

        @Schema(description = "ID da marca (opcional)")
        String brandId,

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 300, message = "Nome deve ter no máximo 300 caracteres")
        String name,

        @NotBlank(message = "Slug é obrigatório")
        @Size(max = 350, message = "Slug deve ter no máximo 350 caracteres")
        String slug,

        String description,

        @Size(max = 500, message = "Descrição curta deve ter no máximo 500 caracteres")
        String shortDescription,

        @NotBlank(message = "SKU é obrigatório")
        @Size(max = 50, message = "SKU deve ter no máximo 50 caracteres")
        String sku,

        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "Preço comparativo não pode ser negativo")
        BigDecimal compareAtPrice,

        @DecimalMin(value = "0.00", message = "Preço de custo não pode ser negativo")
        BigDecimal costPrice,

        @Min(value = 0, message = "Limiar de estoque baixo não pode ser negativo")
        Integer lowStockThreshold,

        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,

        boolean featured
) {}
```

**`UpdateProductResponse`**: record idêntico ao `CreateProductResponse` com factory `from(UpdateProductOutput)`.

**Endpoint no `ProductController`**:
```java
@PatchMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(summary = "Editar produto", description = "Atualiza os dados cadastrais de um produto existente.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "409", description = "Slug ou SKU já utilizado por outro produto"),
        @ApiResponse(responseCode = "422", description = "Regras de negócio violadas")
})
public UpdateProductResponse update(
        @PathVariable final String id,
        @Valid @RequestBody final UpdateProductRequest request
) {
    final var command = new UpdateProductCommand(
            id,
            request.categoryId(),
            request.brandId(),
            request.name(),
            request.slug(),
            request.description(),
            request.shortDescription(),
            request.sku(),
            request.price(),
            request.compareAtPrice(),
            request.costPrice(),
            request.lowStockThreshold(),
            request.weight(),
            request.width(),
            request.height(),
            request.depth(),
            request.featured()
    );
    return UpdateProductResponse.from(
            updateProductUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public UpdateProductUseCase updateProductUseCase(
        final ProductGateway productGateway,
        final TransactionManager transactionManager,
        final DomainEventPublisher eventPublisher
) {
    return new UpdateProductUseCase(productGateway, transactionManager, eventPublisher);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.PRODUCT_NOT_FOUND` | ID não existe ou produto deletado | `404 Not Found` |
| `ProductError.SLUG_ALREADY_EXISTS` | Novo slug já pertence a outro produto | `409 Conflict` |
| `ProductError.SKU_ALREADY_EXISTS` | Novo SKU já pertence a outro produto | `409 Conflict` |
| `ProductError.NAME_EMPTY` | `name` em branco | `422 Unprocessable Entity` |
| `ProductError.NAME_TOO_LONG` | `name` > 300 chars | `422 Unprocessable Entity` |
| `ProductError.SLUG_EMPTY` | `slug` em branco | `422 Unprocessable Entity` |
| `ProductError.SLUG_TOO_LONG` | `slug` > 350 chars | `422 Unprocessable Entity` |
| `ProductError.SLUG_INVALID_FORMAT` | `slug` com maiúsculas ou caracteres inválidos | `422 Unprocessable Entity` |
| `ProductError.SKU_EMPTY` | `sku` em branco | `422 Unprocessable Entity` |
| `ProductError.SKU_TOO_LONG` | `sku` > 50 chars | `422 Unprocessable Entity` |
| `ProductError.SKU_INVALID_FORMAT` | `sku` com chars inválidos | `422 Unprocessable Entity` |
| `ProductError.PRICE_NULL` | `price` nulo | `422 Unprocessable Entity` |
| `ProductError.PRICE_NEGATIVE` | `price` < 0 | `422 Unprocessable Entity` |
| `ProductError.SHORT_DESCRIPTION_TOO_LONG` | `shortDescription` > 500 chars | `422 Unprocessable Entity` |

---

## 🌐 Contrato da API REST

### Request — `PATCH /api/v1/catalog/products/{id}`
```json
{
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
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "featured": true
}
```

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
  "status": "DRAFT",
  "featured": true,
  "quantity": 0,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "createdAt": "2026-04-10T00:00:00Z",
  "updatedAt": "2026-04-10T22:00:00Z",
  "images": []
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Produto não encontrado"],
  "timestamp": "2026-04-10T22:00:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000099"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug deve ter no máximo 350 caracteres", "Preço não pode ser negativo"],
  "timestamp": "2026-04-10T22:00:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **`ProductUpdatedEvent`** — Domain Event simples com `productId`.
2. **Alterar `ProductGateway`** — adicionar `existsBySlugExcludingId()` e `existsBySkuExcludingId()`.
3. **Alterar `Product.java`** — adicionar método `update(...)` com validação via `Notification` e registro do evento.
4. **`UpdateProductCommand`** e **`UpdateProductOutput`** — records no módulo `application`.
5. **`UpdateProductUseCase`** — lógica com `Either`, carregamento por ID, verificação de unicidade e mutação.
6. **Alterar `ProductJpaRepository`** — dois novos métodos de derived query excluindo `id`.
7. **Alterar `ProductPostgresGateway`** — implementar os dois novos métodos com `@Transactional(readOnly = true)`.
8. **`@Bean` em `UseCaseConfig`** — wiring do `UpdateProductUseCase`.
9. **`UpdateProductRequest`**, **`UpdateProductResponse`** e endpoint `PATCH /{id}` no `ProductController`.
10. **Testes unitários** — `Product.update()` (domain), `UpdateProductUseCase` (application) com Mockito.
11. **Testes de integração** (`UpdateProductIT.java` em infrastructure) — Testcontainers + PostgreSQL real.
