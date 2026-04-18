# Task: UC-46 — UpdateCategory

## 📋 Resumo

Permite que administradores editem os dados de uma categoria existente: nome, slug, imagem, descrição, `parent_id` e `sort_order`. A operação é um PUT semântico — o cliente envia o payload completo e todos os campos mutáveis são substituídos.

## 🎯 Objetivo

Expor o endpoint `PUT /api/v1/catalog/categories/{id}` que carrega a categoria, valida unicidade do novo slug (excluindo a própria categoria), verifica existência do novo pai (se alterado), aplica o método `update()` do aggregate e persiste via `gateway.update()`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `PUT /api/v1/catalog/categories/{id}`
* **Tabelas do Banco:** `catalog.categories`
* **Domain Event:** nenhum (update não emite evento neste contexto)

> ⚠️ **Estado atual do projeto**: o `Category` aggregate já possui o método `update()` implementado. O `CategoryGateway` já possui `update(Category)`. A camada de infraestrutura (`CategoryJpaEntity`, `CategoryPostgresGateway`) já está completa. Esta task foca em:
> 1. Adicionar `existsBySlugExcluding` no gateway e repositório (necessário para checar unicidade sem conflito consigo mesmo).
> 2. Implementar `application` e `api` completos.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
| Arquivo | Ação |
|---|---|
| `domain/catalog/gateway/CategoryGateway.java` | ⚠️ **Alterar** — adicionar `existsBySlugExcluding(String slug, CategoryId excludeId)` |

### `application`
1. `application/usecase/catalog/category/UpdateCategoryCommand.java` — **criar**
2. `application/usecase/catalog/category/UpdateCategoryOutput.java` — **criar**
3. `application/usecase/catalog/category/UpdateCategoryUseCase.java` — **criar**

### `infrastructure`
| Arquivo | Ação |
|---|---|
| `infrastructure/catalog/persistence/CategoryJpaRepository.java` | ⚠️ **Alterar** — adicionar `existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id)` |
| `infrastructure/catalog/persistence/CategoryPostgresGateway.java` | ⚠️ **Alterar** — implementar `existsBySlugExcluding` |

### `api`
1. `api/catalog/UpdateCategoryRequest.java` — **criar**
2. `api/catalog/UpdateCategoryResponse.java` — **criar**
3. `api/catalog/CategoryController.java` — ⚠️ **Alterar** — adicionar `PUT /{id}` e `UpdateCategoryUseCase` ao construtor
4. `api/config/UseCaseConfig.java` — ⚠️ **Alterar** — adicionar `@Bean updateCategoryUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway — novo método de unicidade (Domain)

O método `existsBySlug` existente não serve para update, pois retornaria `true` quando o slug informado é o mesmo da própria categoria. Adicionar ao `CategoryGateway`:

```java
/**
 * Verifica se existe categoria ativa com o slug informado,
 * excluindo a categoria com o ID especificado.
 * Usado em atualizações para permitir manter o mesmo slug.
 */
boolean existsBySlugExcluding(String slug, CategoryId excludeId);
```

### 2. Repositório (Infrastructure)

Adicionar ao `CategoryJpaRepository` o método derivado do Spring Data:

```java
boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id);
```

### 3. Gateway concreto (Infrastructure)

Implementar em `CategoryPostgresGateway`:

```java
@Override
@Transactional(readOnly = true)
public boolean existsBySlugExcluding(final String slug, final CategoryId excludeId) {
    return categoryJpaRepository
            .existsBySlugAndDeletedAtIsNullAndIdNot(slug, excludeId.getValue());
}
```

### 4. Contrato de Entrada/Saída (Application)

**`UpdateCategoryCommand`** — payload completo (PUT semântico):

```java
public record UpdateCategoryCommand(
        String categoryId,  // ID da categoria a editar (da URL)
        String parentId,    // nullable — null = manter sem pai (categoria raiz)
        String name,
        String slug,
        String description, // nullable
        String imageUrl,    // nullable
        int    sortOrder
) {}
```

**`UpdateCategoryOutput`** — mesmo shape do `CreateCategoryOutput`:

```java
public record UpdateCategoryOutput(
        String  id,
        String  parentId,
        String  name,
        String  slug,
        String  description,
        String  imageUrl,
        int     sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static UpdateCategoryOutput from(final Category category) { ... }
}
```

### 5. Lógica do Use Case (Application)

```java
public class UpdateCategoryUseCase implements UseCase<UpdateCategoryCommand, UpdateCategoryOutput> {

    private final CategoryGateway categoryGateway;
    private final TransactionManager transactionManager;

    // construtor...

    @Override
    public Either<Notification, UpdateCategoryOutput> execute(final UpdateCategoryCommand command) {
        final var notification = Notification.create();

        // 1. Resolver e validar o ID da categoria
        final CategoryId categoryId;
        try {
            categoryId = CategoryId.from(UUID.fromString(command.categoryId()));
        } catch (IllegalArgumentException e) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        // 2. Carregar a categoria — lança NotFoundException (404) se não existir
        final var categoryOpt = categoryGateway.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            notification.append(CategoryError.CATEGORY_NOT_FOUND);
            return Left(notification);
        }

        final var category = categoryOpt.get();

        // 3. Rejeitar categorias soft-deletadas
        if (category.isDeleted()) {
            notification.append(CategoryError.CATEGORY_ALREADY_DELETED);
            return Left(notification);
        }

        // 4. Unicidade do slug — excluindo a própria categoria
        if (command.slug() != null
                && !command.slug().equals(category.getSlug())
                && categoryGateway.existsBySlugExcluding(command.slug(), categoryId)) {
            notification.append(CategoryError.SLUG_ALREADY_EXISTS);
        }

        // 5. Validar novo parent (quando informado)
        CategoryId newParentId = null;
        if (command.parentId() != null && !command.parentId().isBlank()) {
            try {
                final var candidateId = CategoryId.from(UUID.fromString(command.parentId()));

                // Prevenir referência circular (categoria não pode ser pai de si mesma)
                if (candidateId.getValue().equals(categoryId.getValue())) {
                    notification.append(CategoryError.CIRCULAR_REFERENCE);
                } else {
                    final var parent = categoryGateway.findById(candidateId);
                    if (parent.isEmpty() || parent.get().isDeleted()) {
                        notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
                    } else {
                        newParentId = candidateId;
                    }
                }
            } catch (IllegalArgumentException e) {
                notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 6. Aplicar mutação no aggregate e persistir
        final CategoryId resolvedParentId = newParentId;

        return Try(() -> transactionManager.execute(() -> {
            category.update(
                    resolvedParentId,
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.imageUrl(),
                    command.sortOrder()
            );
            final var updated = categoryGateway.update(category);
            return UpdateCategoryOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> ⚠️ **Sobre `Category.update()`**: diferente de `Category.create()`, o método `update()` do aggregate **não valida** internamente (não chama `CategoryValidator`). A validação de campos (name vazio, slug inválido) é delegada ao Bean Validation do `UpdateCategoryRequest` na camada `api`. O método apenas aplica os valores e atualiza `updatedAt`.

> ⚠️ **Sobre slug igual**: a checagem de unicidade é feita apenas quando o slug muda (`!command.slug().equals(category.getSlug())`). Isso evita falso positivo quando o cliente reenvia o mesmo slug na atualização.

---

### 6. Roteamento e Injeção (API)

**`UpdateCategoryRequest`** — mesmas validações do `CreateCategoryRequest`:

```java
public record UpdateCategoryRequest(
        @JsonProperty("parent_id")
        String parentId,            // nullable — null = categoria raiz

        @NotBlank(message = "'name' é obrigatório")
        @Size(max = 200, message = "'name' deve ter no máximo 200 caracteres")
        String name,

        @NotBlank(message = "'slug' é obrigatório")
        @Size(max = 256, message = "'slug' deve ter no máximo 256 caracteres")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Formato de slug inválido. Use apenas letras minúsculas, números e hífens"
        )
        String slug,

        String description,

        @JsonProperty("image_url")
        @Size(max = 512, message = "'image_url' deve ter no máximo 512 caracteres")
        String imageUrl,

        @JsonProperty("sort_order")
        @Min(value = 0, message = "'sort_order' deve ser >= 0")
        int sortOrder
) {}
```

**`UpdateCategoryResponse`** — mesmo shape do `CreateCategoryResponse`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateCategoryResponse(
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
    public static UpdateCategoryResponse from(final UpdateCategoryOutput output) { ... }
}
```

**`CategoryController`** — adicionar `UpdateCategoryUseCase` ao construtor e novo endpoint:

```java
@PutMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Editar categoria",
        description = "Atualiza todos os campos mutáveis de uma categoria existente (PUT semântico). " +
                      "Campos não enviados são gravados como null. " +
                      "Para manter a categoria sem pai, omitir 'parent_id'. " +
                      "Não é possível editar uma categoria removida."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "422", description = "Categoria não encontrada, deletada, slug em uso, " +
                                                         "referência circular ou categoria pai inválida")
})
public UpdateCategoryResponse update(
        @PathVariable final String id,
        @Valid @RequestBody final UpdateCategoryRequest request
) {
    return UpdateCategoryResponse.from(
            updateCategoryUseCase.execute(new UpdateCategoryCommand(
                    id,
                    request.parentId(),
                    request.name(),
                    request.slug(),
                    request.description(),
                    request.imageUrl(),
                    request.sortOrder()
            )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar após `createCategoryUseCase`:

```java
@Bean
public UpdateCategoryUseCase updateCategoryUseCase(
        final CategoryGateway categoryGateway,
        final TransactionManager transactionManager
) {
    return new UpdateCategoryUseCase(categoryGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `CategoryError.CATEGORY_NOT_FOUND` | ID não existe na base ou UUID inválido | `422 Unprocessable Entity` |
| `CategoryError.CATEGORY_ALREADY_DELETED` | Categoria com `deleted_at != null` | `422 Unprocessable Entity` |
| `CategoryError.SLUG_ALREADY_EXISTS` | Novo slug já usado por outra categoria ativa | `422 Unprocessable Entity` |
| `CategoryError.PARENT_CATEGORY_NOT_FOUND` | `parent_id` não existe ou está soft-deletado | `422 Unprocessable Entity` |
| `CategoryError.CIRCULAR_REFERENCE` | `parent_id` é igual ao `id` da própria categoria | `422 Unprocessable Entity` |
| `MethodArgumentNotValidException` (Bean Validation) | `@NotBlank`, `@Size`, `@Pattern`, `@Min` | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### Request — `PUT /api/v1/catalog/categories/{id}`

```json
{
  "parent_id": "019600ab-dead-7000-a000-000000000001",
  "name": "Smartphones e Celulares",
  "slug": "smartphones-e-celulares",
  "description": "Todos os smartphones e celulares das principais marcas",
  "image_url": "https://cdn.btree.com/categories/smartphones-v2.jpg",
  "sort_order": 2
}
```

> `parent_id`, `description` e `image_url` são opcionais (nullable). `sort_order` é obrigatório (enviar `0` para manter padrão).

### Response (200 OK)

```json
{
  "id": "019600ab-dead-7000-a000-000000000002",
  "parent_id": "019600ab-dead-7000-a000-000000000001",
  "name": "Smartphones e Celulares",
  "slug": "smartphones-e-celulares",
  "description": "Todos os smartphones e celulares das principais marcas",
  "image_url": "https://cdn.btree.com/categories/smartphones-v2.jpg",
  "sort_order": 2,
  "active": true,
  "created_at": "2026-04-10T14:00:00Z",
  "updated_at": "2026-04-10T15:30:00Z"
}
```

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug de categoria já está em uso"],
  "timestamp": "2026-04-10T15:30:00Z",
  "path": "/api/v1/catalog/categories/019600ab-dead-7000-a000-000000000002"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> Banco, domínio estrutural e infraestrutura já estão prontos. Foco nas extensões e nas camadas de application e api.

1. **`CategoryGateway`** — adicionar `existsBySlugExcluding(String slug, CategoryId excludeId)`.
2. **`CategoryJpaRepository`** — adicionar `existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id)`.
3. **`CategoryPostgresGateway`** — implementar `existsBySlugExcluding` com `@Transactional(readOnly = true)`.
4. **`UpdateCategoryCommand`** — record de entrada.
5. **`UpdateCategoryOutput`** — record de saída com `from(Category)`.
6. **`UpdateCategoryUseCase`** — lógica completa com `Either`.
7. **`@Bean` em `UseCaseConfig`** — wiring com `CategoryGateway` e `TransactionManager`.
8. **`UpdateCategoryRequest`** — Bean Validation (mesmo padrão do `CreateCategoryRequest`).
9. **`UpdateCategoryResponse`** — DTO com `from(UpdateCategoryOutput)`.
10. **`CategoryController`** — adicionar `UpdateCategoryUseCase` ao construtor e endpoint `PUT /{id}`.
11. **Testes unitários** (`application/`) — `UpdateCategoryUseCaseTest` com Mockito:
    - Cenário feliz — atualização completa com novo parent
    - Cenário feliz — manter mesmo slug (sem falso positivo de unicidade)
    - Cenário feliz — remover parent (passar `parentId = null`)
    - Categoria não encontrada → `Left` com `CATEGORY_NOT_FOUND`
    - Categoria soft-deletada → `Left` com `CATEGORY_ALREADY_DELETED`
    - Novo slug em uso por outra categoria → `Left` com `SLUG_ALREADY_EXISTS`
    - `parent_id` igual ao próprio ID → `Left` com `CIRCULAR_REFERENCE`
    - `parent_id` de categoria deletada → `Left` com `PARENT_CATEGORY_NOT_FOUND`
