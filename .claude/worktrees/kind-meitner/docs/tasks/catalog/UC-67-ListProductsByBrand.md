# Task: UC-67 — ListProductsByBrand

## 📋 Resumo
Retorna uma lista paginada de produtos **ativos e não-deletados** vinculados a uma marca específica, para consumo público (vitrine, app mobile, integrações). A resposta inclui sumários leves dos produtos com a URL da imagem primária resolvida. Marcas inexistentes ou deletadas resultam em 404. Operação read-only, sem transação de escrita.

## 🎯 Objetivo
Ao receber um `GET /api/v1/catalog/brands/{brandId}/products`, o sistema deve:
1. Verificar que a marca existe e não está deletada (404 se inválida);
2. Buscar produtos com `status = ACTIVE` e `deleted_at IS NULL` vinculados a essa marca;
3. Retornar a página de sumários com metadados de paginação (`page`, `size`, `totalElements`, `totalPages`).

## 📦 Contexto Técnico
* **Módulo Principal:** `application` (query), `infrastructure` (leitura), `api` (roteamento)
* **Prioridade:** `ALTA`
* **Endpoint:** `GET /api/v1/catalog/brands/{brandId}/products`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`, `catalog.brands`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/gateway/ProductGateway.java` — adicionar `Pagination<Product> findActiveByBrandId(BrandId brandId, PageRequest pageRequest)`.

### `application`
1. **CRIAR** `application/.../catalog/product/ListProductsByBrandCommand.java` — record com `brandId`, `page`, `size`.
2. **CRIAR** `application/.../catalog/product/ListProductsByBrandOutput.java` — record com lista paginada de `ProductSummary` e metadados de paginação.
3. **CRIAR** `application/.../catalog/product/ListProductsByBrandUseCase.java` — `QueryUseCase<ListProductsByBrandCommand, ListProductsByBrandOutput>`.

### `infrastructure`
1. **ALTERAR** `infrastructure/.../catalog/persistence/ProductJpaRepository.java` — adicionar `findByBrandIdAndStatusAndDeletedAtIsNull(UUID, ProductStatus, Pageable)`.
2. **ALTERAR** `infrastructure/.../catalog/persistence/ProductPostgresGateway.java` — implementar `findActiveByBrandId()`.

### `api`
1. **CRIAR** `api/.../catalog/ListProductsByBrandResponse.java` — record com factory `from(ListProductsByBrandOutput)`.
2. **ALTERAR** `api/.../catalog/BrandController.java` — adicionar endpoint `GET /{brandId}/products` **sem** `@SecurityRequirement` (endpoint público). Remover a anotação `@SecurityRequirement(name = "bearerAuth")` do nível de classe e mover para os endpoints de escrita (`POST` e `PUT`) individualmente.
3. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar `@Bean` do `ListProductsByBrandUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Domain — nova porta no `ProductGateway`

```java
/**
 * Retorna produtos com {@code status = ACTIVE} e não-deletados de uma marca, paginados.
 * Usado por endpoints públicos de vitrine.
 */
Pagination<Product> findActiveByBrandId(BrandId brandId, PageRequest pageRequest);
```

> `BrandGateway.findById()` já existe — reutilizado para validar a marca antes da busca.
> O `ProductGateway` já possui `findByBrand(BrandId, PageRequest)` (sem filtro de status), mas essa nova porta aplica `status = ACTIVE` diretamente na query, evitando trazer produtos inativos para a memória.

### 2. Contrato de Entrada/Saída (Application)

**`ListProductsByBrandCommand`**:
```java
public record ListProductsByBrandCommand(
        String brandId,
        int page,
        int size
) {}
```

**`ListProductsByBrandOutput`** — lista paginada de sumários leves (mesma estrutura de `ListProductsByCategoryOutput`):
```java
public record ListProductsByBrandOutput(
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

    public static ListProductsByBrandOutput from(final Pagination<Product> page) {
        return new ListProductsByBrandOutput(
                page.items().stream().map(ProductSummary::from).toList(),
                page.currentPage(),
                page.perPage(),
                page.total(),
                page.totalPages()
        );
    }
}
```

> `Pagination<T>` vem de `com.btree.shared.pagination.Pagination` — já presente no projeto. Não usar Spring `Page`.

### 3. Lógica do Use Case (Application)

```java
public class ListProductsByBrandUseCase
        implements QueryUseCase<ListProductsByBrandCommand, ListProductsByBrandOutput> {

    private final ProductGateway productGateway;
    private final BrandGateway   brandGateway;

    public ListProductsByBrandUseCase(
            final ProductGateway productGateway,
            final BrandGateway brandGateway
    ) {
        this.productGateway = productGateway;
        this.brandGateway   = brandGateway;
    }

    @Override
    public Either<Notification, ListProductsByBrandOutput> execute(
            final ListProductsByBrandCommand command) {

        // 1. Validar existência da marca (pré-condição — fora do Either)
        final var brand = brandGateway.findById(BrandId.from(command.brandId()))
                .orElseThrow(() -> NotFoundException.with(BrandError.BRAND_NOT_FOUND));

        if (brand.isDeleted()) {
            throw NotFoundException.with(BrandError.BRAND_NOT_FOUND);
        }

        // 2. Buscar produtos ACTIVE da marca com paginação
        final var pageRequest = PageRequest.of(command.page(), command.size());
        final var result = productGateway.findActiveByBrandId(
                BrandId.from(command.brandId()), pageRequest);

        return Right(ListProductsByBrandOutput.from(result));
    }
}
```

> Query pura: sem `transactionManager`, sem `Try` — toda a operação é read-only.
> Lançar `NotFoundException` diretamente na pré-condição é correto — não é erro de negócio acumulável.
> `BrandId.from(String)` já existe no projeto (ver `CategoryId.from(String)` como referência).

### 4. Persistência (Infrastructure)

**`ProductJpaRepository`** — adicionar:
```java
Page<ProductJpaEntity> findByBrandIdAndStatusAndDeletedAtIsNull(
        UUID brandId,
        ProductStatus status,
        Pageable pageable
);
```

> Já existe `findByBrandIdAndDeletedAtIsNull(UUID, Pageable)` (sem filtro de status). O novo método acrescenta o filtro `status` diretamente na query derivada do Spring Data, evitando trazer registros desnecessários.

**`ProductPostgresGateway`** — implementar:
```java
@Override
@Transactional(readOnly = true)
public Pagination<Product> findActiveByBrandId(
        final BrandId brandId,
        final PageRequest pageRequest
) {
    final var pageable = org.springframework.data.domain.PageRequest.of(
            pageRequest.page(), pageRequest.size(),
            Sort.by(Sort.Direction.ASC, "name"));
    final var page = productJpaRepository.findByBrandIdAndStatusAndDeletedAtIsNull(
            brandId.getValue(), ProductStatus.ACTIVE, pageable);
    return toPagination(page, pageRequest);
}
```

> Ordenação por `name ASC` como padrão — consistente com `findActiveByCategoryId`.

### 5. Roteamento e Injeção (API)

**`ListProductsByBrandResponse`**:
```java
public record ListProductsByBrandResponse(
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
    ) {}

    public static ListProductsByBrandResponse from(final ListProductsByBrandOutput output) {
        return new ListProductsByBrandResponse(
                output.items().stream()
                        .map(i -> new ProductSummary(
                                i.id(), i.name(), i.slug(), i.shortDescription(),
                                i.sku(), i.price(), i.compareAtPrice(),
                                i.status(), i.featured(), i.primaryImageUrl()))
                        .toList(),
                output.page(),
                output.size(),
                output.totalElements(),
                output.totalPages()
        );
    }
}
```

**Endpoint no `BrandController`**:

> ⚠️ A classe `BrandController` atualmente tem `@SecurityRequirement(name = "bearerAuth")` no nível de classe. Como este endpoint é **público** (vitrine), é preciso:
> 1. Remover `@SecurityRequirement(name = "bearerAuth")` da anotação de classe.
> 2. Adicionar `@SecurityRequirement(name = "bearerAuth")` individualmente nos métodos `create()` e `update()`.

```java
@GetMapping("/{brandId}/products")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Produtos da marca",
        description = "Retorna lista paginada de produtos ativos de uma marca. " +
                      "Produtos inativos, em draft ou descontinuados são excluídos. " +
                      "Endpoint público — não requer autenticação.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Marca não encontrada ou deletada")
})
public ListProductsByBrandResponse listByBrand(
        @PathVariable final String brandId,
        @RequestParam(defaultValue = "0")  final int page,
        @RequestParam(defaultValue = "20") final int size
) {
    return ListProductsByBrandResponse.from(
            listProductsByBrandUseCase
                    .execute(new ListProductsByBrandCommand(brandId, page, size))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ListProductsByBrandUseCase listProductsByBrandUseCase(
        final ProductGateway productGateway,
        final BrandGateway brandGateway
) {
    return new ListProductsByBrandUseCase(productGateway, brandGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Situação | Mecanismo | Status HTTP Resultante |
|---|---|---|
| `brandId` não existe ou está deletada | `NotFoundException` lançada diretamente (pré-condição) | `404 Not Found` |
| `page` ou `size` inválidos | Bean Validation no controller (`@Min(0)`) | `400 Bad Request` |

> Este use case não utiliza `Notification` para acumular erros — não há regras de negócio acumuláveis. A única falha possível é pré-condição (`NotFoundException`) ou parâmetro inválido (400).

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/brands/{brandId}/products`
Sem corpo.

| Parâmetro | Tipo | Obrigatório | Default | Descrição |
|---|---|---|---|---|
| `brandId` | `string (UUID)` | Sim | — | ID da marca |
| `page` | `int` | Não | `0` | Número da página (base 0) |
| `size` | `int` | Não | `20` | Itens por página |

### Response (Sucesso — 200 OK)
```json
{
  "items": [
    {
      "id": "01965f3a-0000-7000-0000-000000000020",
      "name": "Camiseta Dry-Fit Performance",
      "slug": "camiseta-dry-fit-performance",
      "shortDescription": "Tecido leve e respirável para treinos intensos.",
      "sku": "CAM-DRY-001-M",
      "price": 129.90,
      "compareAtPrice": 159.90,
      "status": "ACTIVE",
      "featured": true,
      "primaryImageUrl": "https://cdn.btree.com/products/camiseta-dry-fit-front.jpg"
    },
    {
      "id": "01965f3a-0000-7000-0000-000000000021",
      "name": "Short Compressão Pro",
      "slug": "short-compressao-pro",
      "shortDescription": "Short de compressão para corrida e ciclismo.",
      "sku": "SHO-COMP-002-G",
      "price": 179.90,
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
  "errors": ["Marca não encontrada"],
  "timestamp": "2026-04-11T00:00:00Z",
  "path": "/api/v1/catalog/brands/01965f3a-0000-7000-0000-000000000099/products"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **`ProductGateway`** — adicionar `findActiveByBrandId(BrandId, PageRequest)`.
2. **`ListProductsByBrandCommand`** — record com `brandId`, `page`, `size`.
3. **`ListProductsByBrandOutput`** — record paginado com `ProductSummary` aninhado e factory `from(Pagination<Product>)`.
4. **`ListProductsByBrandUseCase`** — `QueryUseCase`; valida marca (existência + soft-delete), chama `productGateway.findActiveByBrandId()`, monta output com `Right(...)`.
5. **`ProductJpaRepository`** — adicionar `findByBrandIdAndStatusAndDeletedAtIsNull(UUID, ProductStatus, Pageable)`.
6. **`ProductPostgresGateway`** — implementar `findActiveByBrandId()` com `Sort.by("name").ascending()`.
7. **`@Bean` em `UseCaseConfig`** — wiring com `ProductGateway` e `BrandGateway`.
8. **`ListProductsByBrandResponse`** — record HTTP com `ProductSummary` aninhado e factory.
9. **`BrandController`** — mover `@SecurityRequirement` da classe para os métodos `create()` e `update()`; adicionar endpoint `GET /{brandId}/products` sem `@SecurityRequirement`.
10. **Testes unitários** — `ListProductsByBrandUseCase` com Mockito:
    - marca válida com produtos → retorna lista paginada correta
    - marca válida sem produtos → retorna lista vazia (items `[]`, `totalElements = 0`)
    - marca não encontrada (`brandGateway.findById()` retorna `Optional.empty()`) → lança `NotFoundException` (404)
    - marca deletada (`brand.isDeleted() = true`) → lança `NotFoundException` (404)
11. **Testes de integração** (`ListProductsByBrandIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real; inserir marca + 3 produtos (2 `ACTIVE`, 1 `INACTIVE`), consultar e verificar que apenas 2 aparecem; verificar paginação.
