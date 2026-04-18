# Task: UC-48 — ListCategories

## 📋 Resumo

Retorna a árvore completa de categorias ativas do catálogo, montada em memória a partir de uma única query. O resultado é uma lista de categorias raiz, cada uma com seus filhos aninhados recursivamente em `children`. Categorias soft-deletadas são excluídas automaticamente.

## 🎯 Objetivo

Expor o endpoint `GET /api/v1/catalog/categories` que busca todas as categorias ativas de uma vez (`findAll()`), monta a hierarquia em memória e retorna a árvore ordenada por `sort_order ASC`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `GET /api/v1/catalog/categories`
* **Tabelas do Banco:** `catalog.categories`

> ✅ **Estado atual do projeto**: toda a infraestrutura já existe. `CategoryGateway.findAll()` retorna todas as categorias ativas ordenadas por `sortOrder ASC, name ASC`. Esta task foca exclusivamente em:
> 1. Implementar `application` e `api`.
> 2. Não há migration, nem alteração em `domain` ou `infrastructure`.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
*Nenhuma alteração necessária.* `CategoryGateway.findAll()` já existe.

### `application`
1. `application/usecase/catalog/category/ListCategoriesOutput.java` — **criar**
2. `application/usecase/catalog/category/ListCategoriesUseCase.java` — **criar**

### `infrastructure`
*Nenhuma alteração necessária.*

### `api`
1. `api/catalog/ListCategoriesResponse.java` — **criar**
2. `api/catalog/CategoryController.java` — ⚠️ **Alterar** — adicionar `ListCategoriesUseCase` ao construtor e endpoint `GET /`
3. `api/config/UseCaseConfig.java` — ⚠️ **Alterar** — adicionar `@Bean listCategoriesUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Contrato de Saída (Application)

**`ListCategoriesOutput`** — record recursivo com `children`:

```java
public record ListCategoriesOutput(
        String                   id,
        String                   parentId,
        String                   name,
        String                   slug,
        String                   description,
        String                   imageUrl,
        int                      sortOrder,
        boolean                  active,
        Instant                  createdAt,
        Instant                  updatedAt,
        List<ListCategoriesOutput> children
) {
    /**
     * Monta a árvore completa a partir de uma lista plana de categorias ativas.
     *
     * <p>Algoritmo O(n):
     * <ol>
     *   <li>Converte a lista plana em {@code Map<parentId, List<Category>>}.</li>
     *   <li>Percorre apenas as raízes (parentId == null), construindo cada nó
     *       recursivamente com {@code buildNode()}.</li>
     * </ol>
     *
     * @param categories todas as categorias ativas (já ordenadas por sortOrder)
     * @return lista de nós raiz com filhos aninhados
     */
    public static List<ListCategoriesOutput> fromTree(final List<Category> categories) {
        final Map<String, List<Category>> byParent = categories.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getParentId() != null
                                ? c.getParentId().getValue().toString()
                                : "__ROOT__"
                ));

        return byParent.getOrDefault("__ROOT__", List.of()).stream()
                .map(root -> buildNode(root, byParent))
                .toList();
    }

    private static ListCategoriesOutput buildNode(
            final Category category,
            final Map<String, List<Category>> byParent
    ) {
        final String id = category.getId().getValue().toString();

        final List<ListCategoriesOutput> children = byParent
                .getOrDefault(id, List.of())
                .stream()
                .map(child -> buildNode(child, byParent))
                .toList();

        return new ListCategoriesOutput(
                id,
                category.getParentId() != null ? category.getParentId().getValue().toString() : null,
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getSortOrder(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                children
        );
    }
}
```

### 2. Lógica do Use Case (Application)

Query pura — sem `TransactionManager`. A transação `readOnly=true` é gerenciada pelo `CategoryPostgresGateway`.

```java
public class ListCategoriesUseCase implements QueryUseCase<Void, List<ListCategoriesOutput>> {

    private final CategoryGateway categoryGateway;

    public ListCategoriesUseCase(final CategoryGateway categoryGateway) {
        this.categoryGateway = categoryGateway;
    }

    @Override
    public Either<Notification, List<ListCategoriesOutput>> execute(final Void ignored) {
        final var categories = categoryGateway.findAll();
        return Right(ListCategoriesOutput.fromTree(categories));
    }
}
```

> ℹ️ `execute(null)` é chamado pelo controller — o parâmetro `Void` é ignorado internamente. Não há cenário de erro de negócio: uma lista vazia é resposta válida.

### 3. Roteamento e Injeção (API)

**`ListCategoriesResponse`** — mesmo shape de `ListCategoriesOutput`, recursivo:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListCategoriesResponse(
        String                     id,
        @JsonProperty("parent_id")  String  parentId,
        String                     name,
        String                     slug,
        String                     description,
        @JsonProperty("image_url")  String  imageUrl,
        @JsonProperty("sort_order") int     sortOrder,
        boolean                    active,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<ListCategoriesResponse> children
) {
    public static ListCategoriesResponse from(final ListCategoriesOutput output) {
        return new ListCategoriesResponse(
                output.id(),
                output.parentId(),
                output.name(),
                output.slug(),
                output.description(),
                output.imageUrl(),
                output.sortOrder(),
                output.active(),
                output.createdAt(),
                output.updatedAt(),
                output.children().stream()
                        .map(ListCategoriesResponse::from)
                        .toList()
        );
    }
}
```

**`CategoryController`** — adicionar `ListCategoriesUseCase` ao construtor e novo endpoint:

```java
@GetMapping
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Listar categorias",
        description = "Retorna a árvore completa de categorias ativas. " +
                      "Cada nó raiz contém seus filhos aninhados em 'children'. " +
                      "Categorias removidas (soft delete) são excluídas automaticamente. " +
                      "A ordem de cada nível respeita 'sort_order ASC'."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Árvore de categorias retornada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
})
public List<ListCategoriesResponse> list() {
    return listCategoriesUseCase.execute(null)
            .getOrElseThrow(n -> DomainException.with(n.getErrors()))
            .stream()
            .map(ListCategoriesResponse::from)
            .toList();
}
```

**`UseCaseConfig.java`** — adicionar após `updateCategoryUseCase`:

```java
@Bean
public ListCategoriesUseCase listCategoriesUseCase(final CategoryGateway categoryGateway) {
    return new ListCategoriesUseCase(categoryGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

Esta query não possui erros de negócio. Lista vazia (`[]`) é resposta legítima quando não há categorias ativas.

| Situação | Comportamento |
|---|---|
| Nenhuma categoria ativa | Retorna `[]` com status `200` |
| Token ausente/inválido | `401` via `AuthenticationEntryPoint` |

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/categories`

Sem body. Sem parâmetros de query.

### Response (200 OK)

```json
[
  {
    "id": "019600ab-dead-7000-a000-000000000001",
    "parent_id": null,
    "name": "Eletrônicos",
    "slug": "eletronicos",
    "description": "Produtos eletrônicos em geral",
    "image_url": "https://cdn.btree.com/categories/eletronicos.jpg",
    "sort_order": 1,
    "active": true,
    "created_at": "2026-04-10T14:00:00Z",
    "updated_at": "2026-04-10T14:00:00Z",
    "children": [
      {
        "id": "019600ab-dead-7000-a000-000000000002",
        "parent_id": "019600ab-dead-7000-a000-000000000001",
        "name": "Smartphones",
        "slug": "smartphones",
        "description": null,
        "image_url": null,
        "sort_order": 1,
        "active": true,
        "created_at": "2026-04-10T14:01:00Z",
        "updated_at": "2026-04-10T14:01:00Z",
        "children": []
      }
    ]
  },
  {
    "id": "019600ab-dead-7000-a000-000000000003",
    "parent_id": null,
    "name": "Moda",
    "slug": "moda",
    "description": null,
    "image_url": null,
    "sort_order": 2,
    "active": true,
    "created_at": "2026-04-10T14:02:00Z",
    "updated_at": "2026-04-10T14:02:00Z",
    "children": []
  }
]
```

> ⚠️ `parent_id`, `description` e `image_url` são omitidos do JSON quando `null` (`@JsonInclude(NON_NULL)`).

---

## 📋 Ordem de Desenvolvimento Sugerida

> Banco, domínio e infraestrutura já estão prontos. Foco exclusivo em `application` e `api`.

1. **`ListCategoriesOutput`** — record recursivo com factory `fromTree(List<Category>)`.
2. **`ListCategoriesUseCase`** — `QueryUseCase<Void, List<ListCategoriesOutput>>`, delega para `findAll()` e monta árvore.
3. **`@Bean` em `UseCaseConfig`** — wiring com `CategoryGateway`.
4. **`ListCategoriesResponse`** — DTO recursivo com factory `from(ListCategoriesOutput)`.
5. **`CategoryController`** — adicionar `ListCategoriesUseCase` ao construtor e endpoint `GET /`.
6. **Testes unitários** (`application/`) — `ListCategoriesUseCaseTest` com Mockito:
   - Cenário feliz — árvore com múltiplos níveis, ordem por `sortOrder`
   - Lista vazia — `findAll()` retorna `[]`, use case retorna `Right([])`
   - Categoria raiz sem filhos — `children` é lista vazia
   - Múltiplos níveis aninhados (3+ níveis) — montagem correta da hierarquia
