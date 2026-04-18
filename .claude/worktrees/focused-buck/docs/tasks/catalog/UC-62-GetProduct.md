# Task: UC-62 — GetProduct

## 📋 Resumo
Retorna o detalhe completo de um produto individual para consumo público (vitrine, app mobile, integrações). A resposta inclui todos os dados cadastrais, lista de imagens, e os dados resumidos de categoria e marca (nome, slug) resolvidos a partir dos IDs armazenados no aggregate. Produtos deletados (`deleted_at IS NOT NULL`) são tratados como inexistentes para clientes públicos.

## 🎯 Objetivo
Ao receber um `GET /api/v1/catalog/products/{id}`, o sistema deve:
1. Localizar o produto pelo ID (404 se não encontrado ou soft-deletado);
2. Enriquecer a resposta com nome e slug da categoria e da marca (quando presentes);
3. Retornar o detalhe completo com status `200 OK`.

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (lógica de query), `infrastructure` (leitura), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `GET /api/v1/catalog/products/{id}`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`, `catalog.categories`, `catalog.brands`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
Nenhum arquivo novo — `ProductGateway`, `CategoryGateway` e `BrandGateway` já possuem `findById()`.

### `application`
1. **CRIAR** `application/.../catalog/product/GetProductCommand.java` — record com `id`.
2. **CRIAR** `application/.../catalog/product/GetProductOutput.java` — output enriquecido com `CategorySummary` e `BrandSummary` aninhados, e factory `from(Product, Category, Brand)`.
3. **CRIAR** `application/.../catalog/product/GetProductUseCase.java` — `QueryUseCase<GetProductCommand, GetProductOutput>`.

### `infrastructure`
Nenhum arquivo novo — todos os gateways necessários já estão implementados.

### `api`
1. **CRIAR** `api/.../catalog/GetProductResponse.java` — record com factory `from(GetProductOutput)`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `GET /{id}`.
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `GetProductUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Domain — sem alterações

`ProductGateway.findById()`, `CategoryGateway.findById()` e `BrandGateway.findById()` já existem e são suficientes. Nenhuma nova porta é necessária.

> **Atenção:** `ProductPostgresGateway.findById()` usa `JpaRepository.findById()` padrão, que **não filtra** `deleted_at`. O use case deve checar `product.isDeleted()` e lançar `NotFoundException` se verdadeiro, garantindo que produtos deletados sejam invisíveis para o endpoint público.

### 2. Contrato de Entrada/Saída (Application)

**`GetProductCommand`**:
```java
public record GetProductCommand(String id) {}
```

**`GetProductOutput`** — com summaries aninhados de categoria e marca:
```java
public record GetProductOutput(
        String id,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        int quantity,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        ProductStatus status,
        boolean featured,
        CategorySummary category,
        BrandSummary brand,
        List<ImageOutput> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record CategorySummary(String id, String name, String slug) {}
    public record BrandSummary(String id, String name, String slug, String logoUrl) {}
    public record ImageOutput(String id, String url, String altText, int sortOrder, boolean primary) {
        public static ImageOutput from(final ProductImage image) { ... }
    }

    /** Hidrata com category e brand opcionais (podem ser nulos se IDs não existirem). */
    public static GetProductOutput from(
            final Product product,
            final Category category,   // nullable
            final Brand brand          // nullable
    ) { ... }
}
```

### 3. Lógica do Use Case (Application)

```java
public class GetProductUseCase implements QueryUseCase<GetProductCommand, GetProductOutput> {

    private final ProductGateway  productGateway;
    private final CategoryGateway categoryGateway;
    private final BrandGateway    brandGateway;

    // construtor com os três gateways

    @Override
    public Either<Notification, GetProductOutput> execute(final GetProductCommand command) {

        // 1. Localizar produto (NotFoundException fora do Either — pré-condição)
        final var product = productGateway.findById(ProductId.from(command.id()))
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

        // 2. Produto soft-deletado = invisível para endpoint público
        if (product.isDeleted()) {
            throw NotFoundException.with(ProductError.PRODUCT_NOT_FOUND);
        }

        // 3. Enriquecer com categoria e marca (opcional — podem não ter sido cadastrados)
        final var category = product.getCategoryId() != null
                ? categoryGateway.findById(product.getCategoryId()).orElse(null)
                : null;

        final var brand = product.getBrandId() != null
                ? brandGateway.findById(product.getBrandId()).orElse(null)
                : null;

        // 4. Montar output (sem transação — leitura pura)
        return Right(GetProductOutput.from(product, category, brand));
    }
}
```

> Query pura: sem `transactionManager`, sem `Try/Either` para infraestrutura — toda a operação é read-only e delegada a `@Transactional(readOnly = true)` nos gateways.

### 4. Persistência (Infrastructure)

Nenhuma mudança. Os três gateways já implementam `findById()` com `@Transactional(readOnly = true)`.

Confirmar que `CategoryGateway` e `BrandGateway` retornam `Optional<Category>` e `Optional<Brand>` respectivamente — já devem estar implementados desde UC-45/UC-52.

### 5. Roteamento e Injeção (API)

**`GetProductResponse`** — replica os campos do `GetProductOutput` com summaries aninhados:
```java
public record GetProductResponse(
        String id,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        int quantity,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        ProductStatus status,
        boolean featured,
        CategoryResponse category,
        BrandResponse brand,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record CategoryResponse(String id, String name, String slug) {}
    public record BrandResponse(String id, String name, String slug, String logoUrl) {}
    public record ImageResponse(String id, String url, String altText, int sortOrder, boolean primary) {}

    public static GetProductResponse from(final GetProductOutput output) { ... }
}
```

**Endpoint no `ProductController`**:
```java
@GetMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Detalhe do produto",
        description = "Retorna dados completos de um produto ativo, incluindo imagens, categoria e marca.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Produto encontrado"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado ou inativo")
})
public GetProductResponse getById(@PathVariable final String id) {
    return GetProductResponse.from(
            getProductUseCase.execute(new GetProductCommand(id))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public GetProductUseCase getProductUseCase(
        final ProductGateway productGateway,
        final CategoryGateway categoryGateway,
        final BrandGateway brandGateway
) {
    return new GetProductUseCase(productGateway, categoryGateway, brandGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Situação | Mecanismo | Status HTTP Resultante |
|---|---|---|
| ID não existe no banco | `NotFoundException` lançada diretamente | `404 Not Found` |
| Produto com `deleted_at` preenchido | `NotFoundException` após `product.isDeleted()` | `404 Not Found` |

> Este use case não usa `Notification` para erros — não há regras de negócio acumuláveis. Qualquer falha é uma pré-condição (`NotFoundException`) ou erro de infraestrutura.

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/products/{id}`
Sem corpo. Sem parâmetros de query.

### Response (Sucesso — 200 OK)
```json
{
  "id": "01965f3a-0000-7000-0000-000000000010",
  "name": "Tênis Running Pro X2",
  "slug": "tenis-running-pro-x2",
  "description": "Tênis de alta performance para corridas de longa distância.",
  "shortDescription": "Tênis running leve e responsivo.",
  "sku": "TEN-RUN-X2-42",
  "price": 499.90,
  "compareAtPrice": 599.90,
  "costPrice": 220.00,
  "quantity": 42,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 30.0,
  "height": 12.0,
  "depth": 20.0,
  "status": "ACTIVE",
  "featured": true,
  "category": {
    "id": "01965f3a-0000-7000-0000-000000000001",
    "name": "Calçados Esportivos",
    "slug": "calcados-esportivos"
  },
  "brand": {
    "id": "01965f3a-0000-7000-0000-000000000002",
    "name": "RunFast",
    "slug": "runfast",
    "logoUrl": "https://cdn.btree.com/brands/runfast-logo.png"
  },
  "images": [
    {
      "id": "01965f3a-0000-7000-0000-000000000020",
      "url": "https://cdn.btree.com/products/tenis-x2-front.jpg",
      "altText": "Tênis Running Pro X2 - Vista frontal",
      "sortOrder": 1,
      "primary": true
    }
  ],
  "createdAt": "2026-04-10T00:00:00Z",
  "updatedAt": "2026-04-10T22:00:00Z"
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

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **`GetProductCommand`** — record com `id`.
2. **`GetProductOutput`** — record com `CategorySummary`, `BrandSummary`, `ImageOutput` aninhados e factory `from(Product, Category, Brand)` tratando nulos.
3. **`GetProductUseCase`** — `QueryUseCase`; carrega produto, verifica `isDeleted()`, resolve categoria e marca, monta output.
4. **`@Bean` em `UseCaseConfig`** — wiring com `ProductGateway`, `CategoryGateway`, `BrandGateway`.
5. **`GetProductResponse`** — record HTTP com `CategoryResponse`, `BrandResponse`, `ImageResponse` e factory `from(GetProductOutput)`.
6. **Endpoint `GET /{id}`** no `ProductController`.
7. **Testes unitários** — `GetProductUseCase` (application) com Mockito: produto ativo com categoria e marca, produto sem categoria/marca (nulos), produto não encontrado (404), produto deletado (404).
8. **Testes de integração** (`GetProductIT.java` em infrastructure) — Testcontainers + PostgreSQL real; inserir produto com categoria e marca, consultar e verificar enriquecimento.
