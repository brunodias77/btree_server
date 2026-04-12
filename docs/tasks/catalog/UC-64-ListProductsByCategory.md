# Task: UC-64 — ListProductsByCategory

## 📋 Resumo
Retorna uma lista paginada de produtos **ativos e não-deletados** pertencentes a uma categoria específica, para consumo público (vitrine, app mobile, integrações). A resposta inclui sumários leves dos produtos (sem todos os campos de custo ou estoque operacional), com a URL da imagem primária resolvida diretamente. Categorias inexistentes ou deletadas resultam em 404. A operação é read-only, sem transação de escrita.

## 🎯 Objetivo
Ao receber um `GET /api/v1/catalog/categories/{categoryId}/products`, o sistema deve:
1. Verificar que a categoria existe e não está deletada (404 se inválida);
2. Buscar produtos com `status = ACTIVE` e `deleted_at IS NULL` vinculados a essa categoria;
3. Retornar a página de sumários com metadados de paginação (`page`, `size`, `totalElements`, `totalPages`).

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (query), `infrastructure` (leitura), `api` (roteamento)
* **Prioridade:** `CRÍTICO`
* **Endpoint:** `GET /api/v1/catalog/categories/{categoryId}/products`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`, `catalog.categories`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/gateway/ProductGateway.java` — adicionar `Page<Product> findByCategoryId(CategoryId categoryId, PageRequest pageRequest)`.

### `application`
1. **CRIAR** `application/.../catalog/product/ListProductsByCategoryCommand.java` — record com `categoryId`, `page`, `size`.
2. **CRIAR** `application/.../catalog/product/ListProductsByCategoryOutput.java` — record com lista paginada de `ProductSummary` e metadados de paginação.
3. **CRIAR** `application/.../catalog/product/ListProductsByCategoryUseCase.java` — `QueryUseCase<ListProductsByCategoryCommand, ListProductsByCategoryOutput>`.

### `infrastructure`
1. **ALTERAR** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — adicionar método de query por `categoryId`.
2. **ALTERAR** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — implementar `findByCategoryId()`.

### `api`
1. **CRIAR** `api/.../catalog/ListProductsByCategoryResponse.java` — record com factory `from(ListProductsByCategoryOutput)`.
2. **ALTERAR** `api/.../catalog/CategoryController.java` — adicionar endpoint `GET /{categoryId}/products`.
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `ListProductsByCategoryUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Domain — nova porta no `ProductGateway`

```java
/**
 * Retorna produtos ACTIVE, não-deletados, de uma categoria, paginados.
 */
Page<Product> findByCategoryId(CategoryId categoryId, PageRequest pageRequest);
```

> `CategoryGateway.findById()` já existe — reutilizado para validar a categoria antes da busca.

### 2. Contrato de Entrada/Saída (Application)

**`ListProductsByCategoryCommand`**:
```java
public record ListProductsByCategoryCommand(
        String categoryId,
        int page,
        int size
) {}
```

**`ListProductsByCategoryOutput`** — lista paginada de sumários leves:
```java
public record ListProductsByCategoryOutput(
        List<ProductSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record ProductSummary(
            String id,
            String name,
            String slug,
            String shortDescription,
            String sku,
            BigDecimal price,
            BigDecimal compareAtPrice,
            ProductStatus status,
            boolean featured,
            String primaryImageUrl    // URL da imagem com primary=true, ou null
    ) {
        public static ProductSummary from(final Product product) {
            final var primaryImage = product.getImages().stream()
                    .filter(ProductImage::isPrimary)
                    .findFirst()
                    .orElse(null);
            return new ProductSummary(
                    product.getId().getValue().toString(),
                    product.getName(),
                    product.getSlug(),
                    product.getShortDescription(),
                    product.getSku(),
                    product.getPrice(),
                    product.getCompareAtPrice(),
                    product.getStatus(),
                    product.isFeatured(),
                    primaryImage != null ? primaryImage.getUrl() : null
            );
        }
    }

    public static ListProductsByCategoryOutput from(final Page<Product> page) {
        return new ListProductsByCategoryOutput(
                page.getContent().stream()
                        .map(ProductSummary::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
```

> `Page<Product>` refere-se a `org.springframework.data.domain.Page` mas **não importado** em `application`. Usar uma abstração própria ou receber a lista + metadados via parâmetros separados no gateway — ver nota abaixo.

> **Nota sobre Page no módulo application:** Como `application` não pode importar Spring, o `ProductGateway` deve retornar um tipo próprio. Usar `com.btree.shared.pagination.Pagination<Product>` (se já existir no módulo `shared`) ou criar um record `PageResult<T>` em `shared`:
> ```java
> public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
> ```
> O gateway retorna `PageResult<Product>` e o `ProductPostgresGateway` converte `Page<ProductJpaEntity>` para `PageResult<Product>` internamente.

### 3. Lógica do Use Case (Application)

```java
public class ListProductsByCategoryUseCase
        implements QueryUseCase<ListProductsByCategoryCommand, ListProductsByCategoryOutput> {

    private final ProductGateway  productGateway;
    private final CategoryGateway categoryGateway;

    // construtor com os dois gateways

    @Override
    public Either<Notification, ListProductsByCategoryOutput> execute(
            final ListProductsByCategoryCommand command) {

        // 1. Validar existência da categoria (pré-condição — fora do Either)
        categoryGateway.findById(CategoryId.from(command.categoryId()))
                .orElseThrow(() -> NotFoundException.with(CategoryError.CATEGORY_NOT_FOUND));

        // 2. Montar paginação
        final var pageRequest = PageRequest.of(command.page(), command.size());

        // 3. Buscar produtos ativos da categoria
        final var result = productGateway.findByCategoryId(
                CategoryId.from(command.categoryId()),
                pageRequest
        );

        // 4. Montar output
        return Right(ListProductsByCategoryOutput.from(result));
    }
}
```

> Query pura: sem `transactionManager`, sem `Try` — toda a operação é read-only. Gateways usam `@Transactional(readOnly = true)`.

> `PageRequest` no módulo `application` — se `application` não pode importar Spring, extrair `pageRequest` como parâmetros primitivos `(int page, int size)` e converter para `org.springframework.data.domain.PageRequest` dentro do `ProductPostgresGateway`.

### 4. Persistência (Infrastructure)

**`ProductJpaRepository`** — adicionar:
```java
Page<ProductJpaEntity> findByCategoryIdAndStatusAndDeletedAtIsNull(
        UUID categoryId,
        ProductStatus status,
        Pageable pageable
);
```

**`ProductPostgresGateway`** — implementar:
```java
@Override
@Transactional(readOnly = true)
public PageResult<Product> findByCategoryId(
        final CategoryId categoryId,
        final int page,
        final int size
) {
    final var pageable = org.springframework.data.domain.PageRequest.of(page, size,
            org.springframework.data.domain.Sort.by("name").ascending());

    final var result = productJpaRepository.findByCategoryIdAndStatusAndDeletedAtIsNull(
            categoryId.getValue(),
            ProductStatus.ACTIVE,
            pageable
    );

    return new PageResult<>(
            result.getContent().stream().map(ProductJpaEntity::toAggregate).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
    );
}
```

> Apenas produtos com `status = ACTIVE` e `deleted_at IS NULL` são retornados. Produtos `INACTIVE`, `DRAFT`, `DISCONTINUED` ou soft-deletados são invisíveis para o endpoint público.

### 5. Roteamento e Injeção (API)

**`ListProductsByCategoryResponse`**:
```java
public record ListProductsByCategoryResponse(
        List<ProductSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record ProductSummaryResponse(
            String id,
            String name,
            String slug,
            String shortDescription,
            String sku,
            BigDecimal price,
            BigDecimal compareAtPrice,
            String status,
            boolean featured,
            String primaryImageUrl
    ) {}

    public static ListProductsByCategoryResponse from(final ListProductsByCategoryOutput output) {
        return new ListProductsByCategoryResponse(
                output.items().stream()
                        .map(s -> new ProductSummaryResponse(
                                s.id(), s.name(), s.slug(), s.shortDescription(), s.sku(),
                                s.price(), s.compareAtPrice(), s.status().name(),
                                s.featured(), s.primaryImageUrl()
                        ))
                        .toList(),
                output.page(),
                output.size(),
                output.totalElements(),
                output.totalPages()
        );
    }
}
```

**Endpoint no `CategoryController`**:
```java
@GetMapping("/{categoryId}/products")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Produtos da categoria",
        description = "Retorna lista paginada de produtos ativos de uma categoria. Produtos inativos, em draft ou descontinuados são excluídos.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
})
public ListProductsByCategoryResponse listByCategory(
        @PathVariable final String categoryId,
        @RequestParam(defaultValue = "0")  final int page,
        @RequestParam(defaultValue = "20") final int size
) {
    return ListProductsByCategoryResponse.from(
            listProductsByCategoryUseCase
                    .execute(new ListProductsByCategoryCommand(categoryId, page, size))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ListProductsByCategoryUseCase listProductsByCategoryUseCase(
        final ProductGateway productGateway,
        final CategoryGateway categoryGateway
) {
    return new ListProductsByCategoryUseCase(productGateway, categoryGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Situação | Mecanismo | Status HTTP Resultante |
|---|---|---|
| `categoryId` não existe ou está deletada | `NotFoundException` lançada diretamente | `404 Not Found` |
| `page` ou `size` negativos | Bean Validation no controller (`@Min(0)`) | `400 Bad Request` |

> Este use case não usa `Notification` para erros — não há regras de negócio acumuláveis. A única falha possível é pré-condição (`NotFoundException`) ou parâmetro inválido.

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/categories/{categoryId}/products`
Sem corpo.

| Parâmetro | Tipo | Obrigatório | Default | Descrição |
|---|---|---|---|---|
| `page` | `int` | Não | `0` | Número da página (base 0) |
| `size` | `int` | Não | `20` | Itens por página (máx 100) |

### Response (Sucesso — 200 OK)
```json
{
  "items": [
    {
      "id": "01965f3a-0000-7000-0000-000000000010",
      "name": "Tênis Running Pro X2",
      "slug": "tenis-running-pro-x2",
      "shortDescription": "Tênis running leve e responsivo.",
      "sku": "TEN-RUN-X2-42",
      "price": 499.90,
      "compareAtPrice": 599.90,
      "status": "ACTIVE",
      "featured": true,
      "primaryImageUrl": "https://cdn.btree.com/products/tenis-x2-front.jpg"
    },
    {
      "id": "01965f3a-0000-7000-0000-000000000011",
      "name": "Tênis Trail Blazer",
      "slug": "tenis-trail-blazer",
      "shortDescription": "Para trilhas e terrenos irregulares.",
      "sku": "TEN-TRL-001",
      "price": 349.90,
      "compareAtPrice": null,
      "status": "ACTIVE",
      "featured": false,
      "primaryImageUrl": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "errors": ["Categoria não encontrada"],
  "timestamp": "2026-04-10T22:00:00Z",
  "path": "/api/v1/catalog/categories/01965f3a-0000-7000-0000-000000000099/products"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Criar `PageResult<T>`** em `shared` (se ainda não existir) — record genérico com `content`, `page`, `size`, `totalElements`, `totalPages`.
2. **Adicionar `findByCategoryId()` em `ProductGateway`** — assinatura com `CategoryId` + `int page` + `int size`, retornando `PageResult<Product>`.
3. **`ListProductsByCategoryCommand`** — record com `categoryId`, `page`, `size`.
4. **`ListProductsByCategoryOutput`** — record paginado com `ProductSummary` aninhado e factory `from(PageResult<Product>)`.
5. **`ListProductsByCategoryUseCase`** — `QueryUseCase`; valida categoria, chama `productGateway.findByCategoryId()`, monta output.
6. **`ProductJpaRepository`** — adicionar `findByCategoryIdAndStatusAndDeletedAtIsNull(UUID, ProductStatus, Pageable)`.
7. **`ProductPostgresGateway`** — implementar `findByCategoryId()` com `Sort.by("name").ascending()` como default.
8. **`@Bean` em `UseCaseConfig`** — wiring com `ProductGateway` e `CategoryGateway`.
9. **`ListProductsByCategoryResponse`** — record HTTP com `ProductSummaryResponse` aninhado e factory.
10. **Endpoint `GET /{categoryId}/products`** no `CategoryController` com parâmetros `page` e `size`.
11. **Testes unitários** — `ListProductsByCategoryUseCase` (application) com Mockito: categoria válida com produtos, categoria válida sem produtos (lista vazia), categoria não encontrada (404).
12. **Testes de integração** (`ListProductsByCategoryIT.java` em infrastructure) — Testcontainers + PostgreSQL real; inserir categoria + 3 produtos (2 ACTIVE, 1 INACTIVE), consultar e verificar que apenas 2 aparecem; verificar paginação.
