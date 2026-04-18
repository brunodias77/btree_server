# Task: UC-63 — SearchProducts

## 📋 Resumo
Busca paginada de produtos com suporte a múltiplos filtros combinados: texto livre (trigram PostgreSQL sobre `name`, `sku` e `short_description`), categoria, marca, faixa de preço, status e destaque. É o endpoint central da vitrine pública e do painel administrativo de produtos. A busca por texto usa o índice GIN/trigram (`pg_trgm`) para tolerância a erros de digitação e busca parcial sem necessidade de motor externo.

## 🎯 Objetivo
Ao receber um `GET /api/v1/catalog/products`, o sistema deve:
1. Aceitar qualquer combinação de filtros via query parameters (todos opcionais);
2. Executar a query dinâmica no banco com os filtros aplicados, respeitando `deleted_at IS NULL`;
3. Retornar uma página de produtos com metadados de paginação (`page`, `size`, `total`, `totalPages`).

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (query), `infrastructure` (JPA Specification), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `GET /api/v1/catalog/products`
* **Tabelas do Banco:** `catalog.products`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **CRIAR** `domain/.../catalog/query/ProductSearchQuery.java` — record de critérios de busca (sem Spring); passado do use case para o gateway.
2. **ALTERAR** `domain/.../catalog/gateway/ProductGateway.java` — adicionar `search(ProductSearchQuery query, PageRequest pageRequest)`.

### `application`
1. **CRIAR** `application/.../catalog/product/SearchProductsCommand.java` — record com todos os filtros e parâmetros de paginação.
2. **CRIAR** `application/.../catalog/product/SearchProductsOutput.java` — record wrapping `Pagination<ProductSummary>` com factory.
3. **CRIAR** `application/.../catalog/product/SearchProductsUseCase.java` — `QueryUseCase<SearchProductsCommand, SearchProductsOutput>`.

### `infrastructure`
1. **CRIAR** `infrastructure/.../catalog/persistence/ProductSpecifications.java` — fábrica de `Specification<ProductJpaEntity>` para cada filtro.
2. **ALTERAR** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — estender `JpaSpecificationExecutor<ProductJpaEntity>`.
3. **ALTERAR** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — implementar `search()` usando as Specifications.
4. **CRIAR** `infrastructure/.../resources/db/migration/V012__add_product_search_index.sql` — extensão `pg_trgm` e índice GIN em `catalog.products`.

### `api`
1. **CRIAR** `api/.../catalog/SearchProductsResponse.java` — record com lista paginada de `ProductSummaryResponse`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `GET /` com `@RequestParam` opcionais.
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `SearchProductsUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Migration Flyway (Infrastructure)

**`V012__add_product_search_index.sql`**:
```sql
-- Habilitar extensão trigram (necessária para GIN de texto)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Índice GIN trigram para busca por nome (case-insensitive)
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON catalog.products USING GIN (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Índice GIN trigram para busca por SKU
CREATE INDEX IF NOT EXISTS idx_products_sku_trgm
    ON catalog.products USING GIN (lower(sku) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Índice GIN trigram para short_description (opcional, para buscas mais amplas)
CREATE INDEX IF NOT EXISTS idx_products_short_description_trgm
    ON catalog.products USING GIN (lower(short_description) gin_trgm_ops)
    WHERE deleted_at IS NULL;
```

### 2. Domain — `ProductSearchQuery` e `ProductGateway`

**`ProductSearchQuery`** — record no `domain`, sem Spring:
```java
package com.btree.domain.catalog.query;

import com.btree.shared.enums.ProductStatus;
import java.math.BigDecimal;

/**
 * Critérios de busca de produtos — passado do use case para o gateway.
 * Todos os campos são opcionais (null = sem filtro).
 */
public record ProductSearchQuery(
        String term,           // busca trigram em name, sku, shortDescription
        String categoryId,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        Boolean featured
) {
    /** Critério vazio — retorna todos os produtos ativos. */
    public static ProductSearchQuery empty() {
        return new ProductSearchQuery(null, null, null, null, null, null, null);
    }
}
```

**`ProductGateway`** — adicionar:
```java
Pagination<Product> search(ProductSearchQuery query, PageRequest pageRequest);
```

### 3. Contrato de Entrada/Saída (Application)

**`SearchProductsCommand`**:
```java
public record SearchProductsCommand(
        String term,
        String categoryId,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String status,      // String → convertida para enum no use case
        Boolean featured,
        int page,
        int size
) {}
```

**`SearchProductsOutput`** — wrapping de `Pagination<ProductSummary>`:
```java
public record SearchProductsOutput(
        List<ProductSummary> items,
        int page,
        int size,
        long total,
        int totalPages
) {
    /** Resumo leve de produto para listagens (sem descrição completa). */
    public record ProductSummary(
            String id,
            String name,
            String slug,
            String sku,
            BigDecimal price,
            BigDecimal compareAtPrice,
            int quantity,
            ProductStatus status,
            boolean featured,
            String primaryImageUrl,   // URL da imagem marcada como primary (null se sem imagem)
            Instant updatedAt
    ) {
        public static ProductSummary from(final Product product) {
            final var primaryImage = product.getImages().stream()
                    .filter(ProductImage::isPrimary)
                    .findFirst()
                    .map(ProductImage::getUrl)
                    .orElse(null);
            return new ProductSummary(
                    product.getId().getValue().toString(),
                    product.getName(),
                    product.getSlug(),
                    product.getSku(),
                    product.getPrice(),
                    product.getCompareAtPrice(),
                    product.getQuantity(),
                    product.getStatus(),
                    product.isFeatured(),
                    primaryImage,
                    product.getUpdatedAt()
            );
        }
    }

    public static SearchProductsOutput from(final Pagination<Product> page) {
        return new SearchProductsOutput(
                page.items().stream().map(ProductSummary::from).toList(),
                page.page(),
                page.size(),
                page.total(),
                (int) Math.ceil((double) page.total() / page.size())
        );
    }
}
```

### 4. Lógica do Use Case (Application)

```java
@Override
public Either<Notification, SearchProductsOutput> execute(final SearchProductsCommand command) {

    // Converter status string para enum (null se não informado)
    ProductStatus status = null;
    if (command.status() != null) {
        try {
            status = ProductStatus.valueOf(command.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            final var notification = Notification.create();
            notification.append(new Error("Status inválido: " + command.status()));
            return Left(notification);
        }
    }

    final var query = new ProductSearchQuery(
            command.term(),
            command.categoryId(),
            command.brandId(),
            command.minPrice(),
            command.maxPrice(),
            status,
            command.featured()
    );

    final var pageRequest = PageRequest.of(command.page(), command.size());
    final var page        = productGateway.search(query, pageRequest);

    return Right(SearchProductsOutput.from(page));
}
```

> Query pura: sem `transactionManager`, sem `Try`. Leitura delegada ao gateway com `@Transactional(readOnly = true)`.

### 5. Persistência (Infrastructure)

**`ProductSpecifications`** — fábrica de predicados Spring Data:
```java
public class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<ProductJpaEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<ProductJpaEntity> withTerm(final String term) {
        if (term == null || term.isBlank()) return null;
        final var pattern = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("sku")), pattern),
                cb.like(cb.lower(root.get("shortDescription")), pattern)
        );
    }

    public static Specification<ProductJpaEntity> withCategory(final String categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) ->
                cb.equal(root.get("categoryId"), UUID.fromString(categoryId));
    }

    public static Specification<ProductJpaEntity> withBrand(final String brandId) {
        if (brandId == null) return null;
        return (root, query, cb) ->
                cb.equal(root.get("brandId"), UUID.fromString(brandId));
    }

    public static Specification<ProductJpaEntity> withMinPrice(final BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<ProductJpaEntity> withMaxPrice(final BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<ProductJpaEntity> withStatus(final ProductStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ProductJpaEntity> withFeatured(final Boolean featured) {
        if (featured == null) return null;
        return (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    /** Combina todos os critérios de um ProductSearchQuery em uma Specification única. */
    public static Specification<ProductJpaEntity> from(final ProductSearchQuery q) {
        return Specification.allOf(
                notDeleted(),
                withTerm(q.term()),
                withCategory(q.categoryId()),
                withBrand(q.brandId()),
                withMinPrice(q.minPrice()),
                withMaxPrice(q.maxPrice()),
                withStatus(q.status()),
                withFeatured(q.featured())
        ).where(notDeleted()); // garante sempre o filtro de soft-delete
    }
}
```

> `Specification.allOf()` ignora automaticamente elementos `null` — cada helper retorna `null` quando o filtro não está presente.

**`ProductJpaRepository`** — adicionar `JpaSpecificationExecutor`:
```java
public interface ProductJpaRepository
        extends JpaRepository<ProductJpaEntity, UUID>,
                JpaSpecificationExecutor<ProductJpaEntity> {
    // métodos existentes permanecem
}
```

**`ProductPostgresGateway`** — implementar `search()`:
```java
@Override
@Transactional(readOnly = true)
public Pagination<Product> search(final ProductSearchQuery query, final PageRequest pageRequest) {
    final var spec     = ProductSpecifications.from(query);
    final var pageable = toPageable(pageRequest);
    final var page     = productJpaRepository.findAll(spec, pageable);
    return toPagination(page, pageRequest);
}
```

### 6. Roteamento e Injeção (API)

**`SearchProductsResponse`**:
```java
public record SearchProductsResponse(
        List<ProductSummaryResponse> items,
        int page,
        int size,
        long total,
        int totalPages
) {
    public record ProductSummaryResponse(
            String id, String name, String slug, String sku,
            BigDecimal price, BigDecimal compareAtPrice,
            int quantity, ProductStatus status, boolean featured,
            String primaryImageUrl, Instant updatedAt
    ) {}

    public static SearchProductsResponse from(final SearchProductsOutput output) {
        return new SearchProductsResponse(
                output.items().stream()
                        .map(i -> new ProductSummaryResponse(
                                i.id(), i.name(), i.slug(), i.sku(),
                                i.price(), i.compareAtPrice(),
                                i.quantity(), i.status(), i.featured(),
                                i.primaryImageUrl(), i.updatedAt()))
                        .toList(),
                output.page(), output.size(), output.total(), output.totalPages()
        );
    }
}
```

**Endpoint no `ProductController`**:
```java
@GetMapping
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Buscar produtos",
        description = "Busca paginada com filtros combinados. Texto livre usa trigram (tolerante a erros).")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultados da busca"),
        @ApiResponse(responseCode = "400", description = "Parâmetros inválidos (ex: status desconhecido)")
})
public SearchProductsResponse search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String categoryId,
        @RequestParam(required = false) String brandId,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean featured,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) {
    final var command = new SearchProductsCommand(
            q, categoryId, brandId, minPrice, maxPrice, status, featured, page, size
    );
    return SearchProductsResponse.from(
            searchProductsUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`**:
```java
@Bean
public SearchProductsUseCase searchProductsUseCase(final ProductGateway productGateway) {
    return new SearchProductsUseCase(productGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Situação | Mecanismo | Status HTTP Resultante |
|---|---|---|
| `status` com valor desconhecido | `notification.append(new Error(...))` → `Left` | `422 Unprocessable Entity` |
| Parâmetros de paginação inválidos (negativos) | Bean Validation no controller (`@Min`) | `400 Bad Request` |

> Busca sem resultados retorna `200 OK` com lista vazia — nunca 404.

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/products`

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `q` | `string` | não | Texto livre (trigram sobre `name`, `sku`, `shortDescription`) |
| `categoryId` | `uuid` | não | Filtrar por categoria |
| `brandId` | `uuid` | não | Filtrar por marca |
| `minPrice` | `decimal` | não | Preço mínimo (inclusive) |
| `maxPrice` | `decimal` | não | Preço máximo (inclusive) |
| `status` | `enum` | não | `ACTIVE`, `INACTIVE`, `DRAFT`, `DISCONTINUED`, `OUT_OF_STOCK` (default público: sem filtro) |
| `featured` | `boolean` | não | Filtrar apenas produtos em destaque |
| `page` | `int` | não | Página (0-based, default `0`) |
| `size` | `int` | não | Itens por página (default `20`, max recomendado `100`) |

### Response (Sucesso — 200 OK)
```json
{
  "items": [
    {
      "id": "01965f3a-0000-7000-0000-000000000010",
      "name": "Tênis Running Pro X2",
      "slug": "tenis-running-pro-x2",
      "sku": "TEN-RUN-X2-42",
      "price": 499.90,
      "compareAtPrice": 599.90,
      "quantity": 42,
      "status": "ACTIVE",
      "featured": true,
      "primaryImageUrl": "https://cdn.btree.com/products/tenis-x2-front.jpg",
      "updatedAt": "2026-04-10T22:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1,
  "totalPages": 1
}
```

### Response (Busca sem resultados — 200 OK)
```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 0,
  "totalPages": 0
}
```

### Response (Erro — 422, status inválido)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Status inválido: ATIVO"],
  "timestamp": "2026-04-10T22:00:00Z",
  "path": "/api/v1/catalog/products"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **`V012__add_product_search_index.sql`** — `CREATE EXTENSION IF NOT EXISTS pg_trgm` + índices GIN parciais em `name`, `sku`, `short_description`.
2. **`ProductSearchQuery`** — record no `domain/catalog/query/`, sem Spring.
3. **Alterar `ProductGateway`** — adicionar `search(ProductSearchQuery, PageRequest)`.
4. **`SearchProductsCommand`** e **`SearchProductsOutput`** (com `ProductSummary`) — records no `application`.
5. **`SearchProductsUseCase`** — conversão de status string → enum, montagem do `ProductSearchQuery`, chamada ao gateway.
6. **`ProductSpecifications`** — `Specification<ProductJpaEntity>` por critério + método `from(ProductSearchQuery)`.
7. **Alterar `ProductJpaRepository`** — estender `JpaSpecificationExecutor`.
8. **Alterar `ProductPostgresGateway`** — implementar `search()` com `@Transactional(readOnly = true)`.
9. **`@Bean` em `UseCaseConfig`** — wiring do `SearchProductsUseCase`.
10. **`SearchProductsResponse`** + endpoint `GET /` no `ProductController` com `@RequestParam` opcionais.
11. **Testes unitários** — `SearchProductsUseCase` com Mockito: busca vazia, com term, com filtros combinados, status inválido.
12. **Testes de integração** (`SearchProductsIT.java`) — Testcontainers; inserir produtos variados, testar trigram com erro de digitação, combinação de filtros e paginação.
