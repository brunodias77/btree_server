# Task: UC-66 — ListFeaturedProducts

## 📋 Resumo
Retorna uma lista paginada dos produtos marcados como **destaque** (`featured = true`), ativos e não-deletados, para consumo público. Usada em seções de vitrine como "Produtos em Destaque", carrosséis da home e widgets promocionais. A resposta é um sumário leve — sem campos operacionais de custo ou estoque — com a URL da imagem primária resolvida.

## 🎯 Objetivo
Ao receber um `GET /api/v1/catalog/products/featured`, o sistema deve:
1. Buscar produtos com `featured = true`, `status = ACTIVE` e `deleted_at IS NULL`;
2. Retornar a página de sumários com metadados de paginação (`page`, `size`, `totalElements`, `totalPages`).

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (query), `infrastructure` (leitura), `api` (roteamento)
* **Prioridade:** `ALTA`
* **Endpoint:** `GET /api/v1/catalog/products/featured`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/gateway/ProductGateway.java` — adicionar `PageResult<Product> findFeatured(int page, int size)`.

### `application`
1. **CRIAR** `application/.../catalog/product/ListFeaturedProductsCommand.java` — record com `page`, `size`.
2. **CRIAR** `application/.../catalog/product/ListFeaturedProductsOutput.java` — record paginado com `ProductSummary` aninhado.
3. **CRIAR** `application/.../catalog/product/ListFeaturedProductsUseCase.java` — `QueryUseCase<ListFeaturedProductsCommand, ListFeaturedProductsOutput>`.

### `infrastructure`
1. **ALTERAR** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — adicionar método de query por `featured`.
2. **ALTERAR** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — implementar `findFeatured()`.

### `api`
1. **CRIAR** `api/.../catalog/ListFeaturedProductsResponse.java` — record com factory `from(ListFeaturedProductsOutput)`.
2. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar endpoint `GET /featured`.
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `ListFeaturedProductsUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Domain — nova porta no `ProductGateway`

```java
/**
 * Retorna produtos ACTIVE, featured=true, não-deletados, paginados.
 */
PageResult<Product> findFeatured(int page, int size);
```

> `PageResult<T>` definido em `shared` — ver UC-64 para detalhes do record.

### 2. Contrato de Entrada/Saída (Application)

**`ListFeaturedProductsCommand`**:
```java
public record ListFeaturedProductsCommand(int page, int size) {}
```

**`ListFeaturedProductsOutput`** — mesma estrutura paginada de UC-64:
```java
public record ListFeaturedProductsOutput(
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
            String primaryImageUrl
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

    public static ListFeaturedProductsOutput from(final PageResult<Product> page) {
        return new ListFeaturedProductsOutput(
                page.content().stream().map(ProductSummary::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
public class ListFeaturedProductsUseCase
        implements QueryUseCase<ListFeaturedProductsCommand, ListFeaturedProductsOutput> {

    private final ProductGateway productGateway;

    public ListFeaturedProductsUseCase(final ProductGateway productGateway) {
        this.productGateway = productGateway;
    }

    @Override
    public Either<Notification, ListFeaturedProductsOutput> execute(
            final ListFeaturedProductsCommand command) {

        final var result = productGateway.findFeatured(command.page(), command.size());
        return Right(ListFeaturedProductsOutput.from(result));
    }
}
```

> Query pura: sem `transactionManager`, sem `Try`, sem pré-condições. Toda operação delegada ao gateway com `@Transactional(readOnly = true)`.

### 4. Persistência (Infrastructure)

**`ProductJpaRepository`** — adicionar:
```java
Page<ProductJpaEntity> findByFeaturedTrueAndStatusAndDeletedAtIsNull(
        ProductStatus status,
        Pageable pageable
);
```

**`ProductPostgresGateway`** — implementar:
```java
@Override
@Transactional(readOnly = true)
public PageResult<Product> findFeatured(final int page, final int size) {
    final var pageable = org.springframework.data.domain.PageRequest.of(
            page, size,
            org.springframework.data.domain.Sort.by("name").ascending()
    );

    final var result = productJpaRepository
            .findByFeaturedTrueAndStatusAndDeletedAtIsNull(ProductStatus.ACTIVE, pageable);

    return new PageResult<>(
            result.getContent().stream().map(ProductJpaEntity::toAggregate).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
    );
}
```

> Apenas `status = ACTIVE` e `deleted_at IS NULL` são retornados. O filtro `featured = true` é expressado no nome do método Spring Data via `findByFeaturedTrue`.

### 5. Roteamento e Injeção (API)

**`ListFeaturedProductsResponse`**:
```java
public record ListFeaturedProductsResponse(
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

    public static ListFeaturedProductsResponse from(final ListFeaturedProductsOutput output) {
        return new ListFeaturedProductsResponse(
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

**Endpoint no `ProductController`**:

> ⚠️ **Atenção ao mapeamento de rotas:** `GET /featured` deve ser declarado **antes** de `GET /{id}` no controller para evitar que o Spring interprete `"featured"` como um `{id}`. Usar `@GetMapping("/featured")` explicitamente.

```java
@GetMapping("/featured")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Produtos em destaque",
        description = "Retorna lista paginada de produtos ativos marcados como destaque (featured=true).")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
})
public ListFeaturedProductsResponse listFeatured(
        @RequestParam(defaultValue = "0")  @Min(0) final int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) final int size
) {
    return ListFeaturedProductsResponse.from(
            listFeaturedProductsUseCase
                    .execute(new ListFeaturedProductsCommand(page, size))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ListFeaturedProductsUseCase listFeaturedProductsUseCase(
        final ProductGateway productGateway
) {
    return new ListFeaturedProductsUseCase(productGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

Este use case não produz erros de domínio — não há pré-condições acumuláveis. A única possibilidade de falha é parâmetro inválido (tratado pelo Bean Validation no controller).

| Situação | Mecanismo | Status HTTP Resultante |
|---|---|---|
| `page` negativo | `@Min(0)` no controller | `400 Bad Request` |
| `size` fora do intervalo `[1, 100]` | `@Min(1) @Max(100)` no controller | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/products/featured`
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
      "id": "01965f3a-0000-7000-0000-000000000012",
      "name": "Mochila Urban Trail",
      "slug": "mochila-urban-trail",
      "shortDescription": "Mochila resistente para uso diário e trilhas leves.",
      "sku": "MCH-URB-001",
      "price": 289.90,
      "compareAtPrice": null,
      "status": "ACTIVE",
      "featured": true,
      "primaryImageUrl": "https://cdn.btree.com/products/mochila-urban.jpg"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

> Lista vazia (`"items": []`, `"totalElements": 0`) é resposta válida quando não há produtos em destaque — não é 404.

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Verificar `PageResult<T>` em `shared`** — deve existir desde UC-64; criar se ainda não existir.
2. **Adicionar `findFeatured(int, int)` em `ProductGateway`** — retorna `PageResult<Product>`.
3. **`ListFeaturedProductsCommand`** — record com `page` e `size`.
4. **`ListFeaturedProductsOutput`** — record paginado com `ProductSummary` aninhado e factory `from(PageResult<Product>)`.
5. **`ListFeaturedProductsUseCase`** — `QueryUseCase`; delega diretamente ao gateway, sem pré-condições.
6. **`ProductJpaRepository`** — adicionar `findByFeaturedTrueAndStatusAndDeletedAtIsNull(ProductStatus, Pageable)`.
7. **`ProductPostgresGateway`** — implementar `findFeatured()` com sort por nome.
8. **`@Bean` em `UseCaseConfig`** — apenas `ProductGateway` como dependência.
9. **`ListFeaturedProductsResponse`** — record HTTP com `ProductSummaryResponse` aninhado e factory.
10. **Endpoint `GET /featured`** no `ProductController` — declarar **antes** de `GET /{id}` para evitar conflito de rota.
11. **Testes unitários** — `ListFeaturedProductsUseCase` com Mockito: retorna lista, retorna lista vazia.
12. **Testes de integração** (`ListFeaturedProductsIT.java`) — Testcontainers; inserir 3 produtos (2 featured ACTIVE, 1 featured INACTIVE, 1 não-featured ACTIVE); verificar que apenas os 2 featured ACTIVE aparecem.
