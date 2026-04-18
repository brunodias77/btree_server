# Task: UC-47 — GetCategory

## 📋 Resumo

Permite consultar os dados completos de uma categoria individual pelo seu UUID. Utilizado pelo front-end de administração para exibir o formulário de edição e pelo catálogo público para renderizar a página de categoria com breadcrumb.

## 🎯 Objetivo

Expor o endpoint `GET /api/v1/catalog/categories/{id}` que carrega a categoria pelo ID, rejeita categorias soft-deletadas com `422` e retorna o payload completo. Use case de leitura pura — sem `TransactionManager`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `GET /api/v1/catalog/categories/{id}`
* **Tabelas do Banco:** `catalog.categories`
* **Domain Event:** nenhum (query pura)

> ⚠️ **Estado atual do projeto**: toda a camada de domínio e infraestrutura já está completa. `CategoryGateway.findById()` já existe e retorna `Optional<Category>` **incluindo** soft-deletadas — o use case deve checar `isDeleted()` explicitamente. Esta task implementa exclusivamente a camada `application` (Command, Output, UseCase) e adiciona o endpoint ao `CategoryController` existente.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
> Nenhuma alteração necessária.

### `application`
1. `application/usecase/catalog/category/GetCategoryCommand.java` — **criar**
2. `application/usecase/catalog/category/GetCategoryOutput.java` — **criar**
3. `application/usecase/catalog/category/GetCategoryUseCase.java` — **criar**

### `infrastructure`
> Nenhuma alteração necessária. `CategoryPostgresGateway.findById()` já está implementado com `@Transactional(readOnly = true)`.

### `api`
1. `api/catalog/GetCategoryResponse.java` — **criar**
2. `api/catalog/CategoryController.java` — ⚠️ **Alterar** — adicionar `GetCategoryUseCase` ao construtor e endpoint `GET /{id}`
3. `api/config/UseCaseConfig.java` — ⚠️ **Alterar** — adicionar `@Bean getCategoryUseCase`

> Não há `GetCategoryRequest` — a entrada é apenas o path variable `{id}`, resolvido diretamente no controller.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Contrato de Entrada/Saída (Application)

**`GetCategoryCommand`** — record com o ID como `String` (UUID bruto da URL):

```java
/**
 * Comando de entrada para UC-47 — GetCategory.
 *
 * @param categoryId UUID da categoria a consultar (extraído do path variable)
 */
public record GetCategoryCommand(String categoryId) {}
```

**`GetCategoryOutput`** — mesmo shape dos outputs de create/update:

```java
public record GetCategoryOutput(
        String  id,
        String  parentId,   // nullable — null = categoria raiz
        String  name,
        String  slug,
        String  description,
        String  imageUrl,
        int     sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static GetCategoryOutput from(final Category category) { ... }
}
```

---

### 2. Lógica do Use Case (Application)

> Query pura — implementa `QueryUseCase<GetCategoryCommand, GetCategoryOutput>`. Sem `TransactionManager` (leitura delegada ao gateway com `@Transactional(readOnly = true)`).

```java
public class GetCategoryUseCase implements QueryUseCase<GetCategoryCommand, GetCategoryOutput> {

    private final CategoryGateway categoryGateway;

    public GetCategoryUseCase(final CategoryGateway categoryGateway) {
        this.categoryGateway = categoryGateway;
    }

    @Override
    public Either<Notification, GetCategoryOutput> execute(final GetCategoryCommand command) {
        final var notification = Notification.create();

        // 1. Validar e parsear UUID
        final CategoryId categoryId;
        try {
            categoryId = CategoryId.from(UUID.fromString(command.categoryId()));
        } catch (IllegalArgumentException e) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        // 2. Buscar categoria (findById inclui soft-deletadas)
        final var categoryOpt = categoryGateway.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        final var category = categoryOpt.get();

        // 3. Rejeitar soft-deletadas
        if (category.isDeleted()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        return Right(GetCategoryOutput.from(category));
    }
}
```

> ⚠️ **Soft-delete**: para categorias removidas retorna `CATEGORY_NOT_FOUND` (não `CATEGORY_ALREADY_DELETED`). Expor que um recurso foi deletado é um vazamento de informação desnecessário para endpoints públicos de consulta.

> ⚠️ **`findById` inclui deletados**: o `CategoryPostgresGateway.findById()` usa `repository.findById(UUID)` herdado do `JpaRepository`, sem filtro `deleted_at IS NULL`. Isso é intencional — permite que use cases de escrita (update, delete) validem o estado atual. Neste use case de leitura, a checagem de `isDeleted()` é feita manualmente após o retorno do gateway.

---

### 3. Roteamento e Injeção (API)

**`GetCategoryResponse`** — mesmo shape dos responses de create/update:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetCategoryResponse(
        String  id,
        @JsonProperty("parent_id")  String  parentId,
        String  name,
        String  slug,
        String  description,
        @JsonProperty("image_url")  String  imageUrl,
        @JsonProperty("sort_order") int     sortOrder,
        boolean active,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static GetCategoryResponse from(final GetCategoryOutput output) { ... }
}
```

**`CategoryController`** — adicionar `GetCategoryUseCase` ao construtor e endpoint:

```java
@GetMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Consultar categoria",
        description = "Retorna os dados completos de uma categoria pelo UUID. " +
                      "Categorias removidas (soft delete) retornam 422."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "422", description = "Categoria não encontrada ou removida")
})
public GetCategoryResponse getById(@PathVariable final String id) {
    return GetCategoryResponse.from(
            getCategoryUseCase.execute(new GetCategoryCommand(id))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar após `createCategoryUseCase`:

```java
@Bean
public GetCategoryUseCase getCategoryUseCase(final CategoryGateway categoryGateway) {
    return new GetCategoryUseCase(categoryGateway);
}
```

> Sem `TransactionManager` no construtor — query pura delega transação ao gateway.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `CategoryError.CATEGORY_NOT_FOUND` | UUID inválido, categoria inexistente ou soft-deletada | `422 Unprocessable Entity` |

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/catalog/categories/{id}`

Sem body. Apenas path variable `{id}` (UUID).

### Response (200 OK)

```json
{
  "id": "019600ab-dead-7000-a000-000000000002",
  "parent_id": "019600ab-dead-7000-a000-000000000001",
  "name": "Smartphones",
  "slug": "smartphones",
  "description": "Celulares e smartphones das principais marcas",
  "image_url": "https://cdn.btree.com/categories/smartphones.jpg",
  "sort_order": 1,
  "active": true,
  "created_at": "2026-04-10T14:00:00Z",
  "updated_at": "2026-04-10T14:00:00Z"
}
```

> Campos `parent_id`, `description` e `image_url` são omitidos do JSON quando `null` (`@JsonInclude(NON_NULL)`).

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Categoria não encontrada"],
  "timestamp": "2026-04-10T14:00:00Z",
  "path": "/api/v1/catalog/categories/019600ab-dead-7000-a000-000000000002"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> Banco, domínio e infraestrutura já estão prontos. Implementação restrita a `application` e `api`.

1. **`GetCategoryCommand`** — record com `categoryId`.
2. **`GetCategoryOutput`** — record com `from(Category)`.
3. **`GetCategoryUseCase`** — `QueryUseCase` com checagem de `isDeleted()`.
4. **`@Bean` em `UseCaseConfig`** — apenas `CategoryGateway` (sem `TransactionManager`).
5. **`GetCategoryResponse`** — DTO com `@JsonInclude(NON_NULL)` e `from(GetCategoryOutput)`.
6. **`CategoryController`** — adicionar `GetCategoryUseCase` ao construtor e endpoint `GET /{id}`.
7. **Testes unitários** (`application/`) — `GetCategoryUseCaseTest` com Mockito:
   - Cenário feliz — categoria ativa encontrada e retornada
   - Cenário feliz — categoria raiz (`parentId = null`)
   - UUID inválido → `Left` com `CATEGORY_NOT_FOUND`
   - Categoria não encontrada (empty Optional) → `Left` com `CATEGORY_NOT_FOUND`
   - Categoria soft-deletada → `Left` com `CATEGORY_NOT_FOUND`
   - Categoria inativa (`active = false`, não deletada) → `Right` (ativa/inativa é dado, não filtro)
