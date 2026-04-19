# Task: UC-45 — CreateCategory

## 📋 Resumo

Permite que administradores cadastrem novas categorias de produtos no catálogo, com suporte a hierarquia ilimitada via `parent_id` auto-referenciado. Categorias são a principal forma de navegação e filtragem de produtos pelo cliente final.

## 🎯 Objetivo

Expor o endpoint `POST /api/v1/catalog/categories` que recebe os dados da nova categoria, valida unicidade do slug, verifica a existência do pai (quando informado), persiste via soft-insert e publica o `CategoryCreatedEvent` para consumers downstream.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/categories`
* **Tabelas do Banco:** `catalog.categories`
* **Domain Event:** `CategoryCreatedEvent` (`category.created`) — já implementado

> ⚠️ **Estado atual do projeto**: as camadas `domain` e `infrastructure` já estão completamente implementadas para `Category`. Esta task implementa exclusivamente a camada `application` (Command, Output, UseCase) e a camada `api` (Request, Response, Controller, @Bean).

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
> Todos os arquivos abaixo já existem. Nenhuma alteração necessária.

| Arquivo | Status |
|---|---|
| `domain/catalog/entity/Category.java` | ✅ Existe |
| `domain/catalog/gateway/CategoryGateway.java` | ✅ Existe |
| `domain/catalog/error/CategoryError.java` | ⚠️ Alterar — adicionar `PARENT_CATEGORY_NOT_FOUND` |
| `domain/catalog/identifier/CategoryId.java` | ✅ Existe |
| `domain/catalog/events/CategoryCreatedEvent.java` | ✅ Existe |
| `domain/catalog/validator/CategoryValidator.java` | ✅ Existe |

### `application`
1. `application/usecase/catalog/category/CreateCategoryCommand.java` — **criar**
2. `application/usecase/catalog/category/CreateCategoryOutput.java` — **criar**
3. `application/usecase/catalog/category/CreateCategoryUseCase.java` — **criar**

### `infrastructure`
> Todos os arquivos abaixo já existem. Nenhuma alteração necessária.

| Arquivo | Status |
|---|---|
| `infrastructure/catalog/entity/CategoryJpaEntity.java` | ✅ Existe |
| `infrastructure/catalog/persistence/CategoryJpaRepository.java` | ✅ Existe |
| `infrastructure/catalog/persistence/CategoryPostgresGateway.java` | ✅ Existe |

### `api`
1. `api/catalog/CategoryController.java` — **criar**
2. `api/catalog/CreateCategoryRequest.java` — **criar**
3. `api/catalog/CreateCategoryResponse.java` — **criar**
4. `api/config/UseCaseConfig.java` — **alterar** — adicionar `@Bean createCategoryUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Alteração de Domínio — `CategoryError`

Adicionar a constante ausente que o use case precisará para rejeitar um `parent_id` inválido:

```java
public static final Error PARENT_CATEGORY_NOT_FOUND =
        new Error("Categoria pai não encontrada ou foi removida");
```

---

### 2. Contrato de Entrada/Saída (Application)

**`CreateCategoryCommand`** — record imutável, sem entidades de domínio:

```java
public record CreateCategoryCommand(
        String parentId,    // nullable — ausente = categoria raiz
        String name,
        String slug,
        String description, // nullable
        String imageUrl,    // nullable
        int    sortOrder
) {}
```

**`CreateCategoryOutput`** — record com factory `from(Category)`:

```java
public record CreateCategoryOutput(
        String  id,
        String  parentId,   // nullable
        String  name,
        String  slug,
        String  description,
        String  imageUrl,
        int     sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CreateCategoryOutput from(final Category category) { ... }
}
```

---

### 3. Lógica do Use Case (Application)

> ⚠️ **Atenção ao padrão de `Category.create()`**: diferente de `User.create()`, o factory method de `Category` cria seu próprio `Notification` interno e lança `DomainException` se houver erro de validação. Por isso, a chamada a `Category.create()` deve ficar **dentro** do bloco `Try`, onde qualquer exceção é capturada e convertida em `Left(Notification)`.

```java
public class CreateCategoryUseCase implements UseCase<CreateCategoryCommand, CreateCategoryOutput> {

    private final CategoryGateway categoryGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    // construtor...

    @Override
    public Either<Notification, CreateCategoryOutput> execute(final CreateCategoryCommand command) {
        final var notification = Notification.create();

        // 1. Unicidade do slug (apenas em categorias não soft-deletadas)
        if (command.slug() != null && categoryGateway.existsBySlug(command.slug())) {
            notification.append(CategoryError.SLUG_ALREADY_EXISTS);
        }

        // 2. Validar parent (quando informado)
        CategoryId parentId = null;
        if (command.parentId() != null && !command.parentId().isBlank()) {
            try {
                parentId = CategoryId.from(UUID.fromString(command.parentId()));
                final var parent = categoryGateway.findById(parentId);
                if (parent.isEmpty() || parent.get().isDeleted()) {
                    notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
                    parentId = null; // evita uso posterior
                }
            } catch (IllegalArgumentException e) {
                notification.append(CategoryError.PARENT_CATEGORY_NOT_FOUND);
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Criar aggregate dentro do Try — Category.create() lança DomainException
        //    se as invariantes (name, slug format) falharem; Try converte para Left.
        final CategoryId resolvedParentId = parentId;

        return Try(() -> transactionManager.execute(() -> {
            final var category = Category.create(
                    resolvedParentId,
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.imageUrl(),
                    command.sortOrder()
            );
            final var saved = categoryGateway.save(category);
            eventPublisher.publishAll(category.getDomainEvents());
            return CreateCategoryOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }
}
```

---

### 4. Persistência (Infrastructure)

> Toda a camada de persistência já está implementada. Consultar os arquivos existentes como referência:

**`CategoryJpaEntity`** — `infrastructure/catalog/entity/CategoryJpaEntity.java`
- `@Entity @Table(name = "categories", schema = "catalog")`
- Sem coluna `version` (sem optimistic locking em categorias)
- `parentId` armazenado como `UUID` simples (sem `@ManyToOne`) para evitar N+1
- Métodos: `from(Category)`, `toAggregate()`, `updateFrom(Category)`

**`CategoryJpaRepository`** — `infrastructure/catalog/persistence/CategoryJpaRepository.java`
- `existsBySlugAndDeletedAtIsNull(String slug)` — usado na checagem de unicidade
- `findById(UUID)` — herdado de `JpaRepository`, inclui soft-deletados (correto para validação do parent)

**`CategoryPostgresGateway`** — `infrastructure/catalog/persistence/CategoryPostgresGateway.java`
- `@Component @Transactional`
- `save(Category)` → `CategoryJpaEntity.from(category)` + `repository.save()`
- `existsBySlug(String)` → delega para `existsBySlugAndDeletedAtIsNull`
- `findById(CategoryId)` → `@Transactional(readOnly = true)`, retorna `Optional<Category>` incluindo deletados (necessário para bloquear uso de pai deletado)

---

### 5. Roteamento e Injeção (API)

**`CreateCategoryRequest`** — record com Bean Validation:

```java
public record CreateCategoryRequest(
        @JsonProperty("parent_id")
        String parentId,            // nullable

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

        String description,         // nullable

        @JsonProperty("image_url")
        @Size(max = 512, message = "'image_url' deve ter no máximo 512 caracteres")
        String imageUrl,            // nullable

        @JsonProperty("sort_order")
        @Min(value = 0, message = "'sort_order' deve ser >= 0")
        int sortOrder               // default 0
) {}
```

**`CreateCategoryResponse`** — record com factory `from(CreateCategoryOutput)`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateCategoryResponse(
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
    public static CreateCategoryResponse from(final CreateCategoryOutput output) { ... }
}
```

**`CategoryController`**:

```java
@RestController
@RequestMapping("/api/v1/catalog/categories")
@Tag(name = "Categories", description = "Gerenciamento de categorias do catálogo de produtos")
public class CategoryController {

    private final CreateCategoryUseCase createCategoryUseCase;

    public CategoryController(final CreateCategoryUseCase createCategoryUseCase) {
        this.createCategoryUseCase = createCategoryUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Criar categoria",
            description = "Cria uma nova categoria de produtos. " +
                          "Omitir 'parent_id' cria uma categoria raiz. " +
                          "O slug deve ser único entre categorias ativas e seguir o formato kebab-case."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de slug, campos obrigatórios)"),
            @ApiResponse(responseCode = "422", description = "Slug já em uso ou categoria pai não encontrada")
    })
    public CreateCategoryResponse create(@Valid @RequestBody final CreateCategoryRequest request) {
        final var command = new CreateCategoryCommand(
                request.parentId(),
                request.name(),
                request.slug(),
                request.description(),
                request.imageUrl(),
                request.sortOrder()
        );
        return CreateCategoryResponse.from(
                createCategoryUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

**`UseCaseConfig.java`** — adicionar ao bloco `// ── Catalog ───`:

```java
@Bean
public CreateCategoryUseCase createCategoryUseCase(
        final CategoryGateway categoryGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new CreateCategoryUseCase(categoryGateway, eventPublisher, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `CategoryError.SLUG_ALREADY_EXISTS` | Já existe categoria ativa com o mesmo slug | `422 Unprocessable Entity` |
| `CategoryError.PARENT_CATEGORY_NOT_FOUND` | `parent_id` fornecido não existe ou está soft-deletado | `422 Unprocessable Entity` |
| `CategoryError.NAME_EMPTY` | `name` nulo ou em branco | `422 Unprocessable Entity` |
| `CategoryError.NAME_TOO_LONG` | `name` > 200 caracteres | `422 Unprocessable Entity` |
| `CategoryError.SLUG_EMPTY` | `slug` nulo ou em branco | `422 Unprocessable Entity` |
| `CategoryError.SLUG_TOO_LONG` | `slug` > 256 caracteres | `422 Unprocessable Entity` |
| `CategoryError.SLUG_INVALID_FORMAT` | `slug` não bate com `^[a-z0-9]+(?:-[a-z0-9]+)*$` | `422 Unprocessable Entity` |
| `MethodArgumentNotValidException` (Bean Validation) | Campos `@NotBlank`, `@Size`, `@Pattern`, `@Min` | `400 Bad Request` |

> Erros de unicidade de slug são detectados **antes** de chamar `Category.create()` (validação no use case). Erros de formato de name/slug também são verificados pelo `CategoryValidator` internamente no `Category.create()` e surfaceiam via `DomainException` capturado pelo `Try`.

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/categories`

```json
{
  "parent_id": "019600ab-dead-7000-a000-000000000001",
  "name": "Smartphones",
  "slug": "smartphones",
  "description": "Celulares e smartphones das principais marcas",
  "image_url": "https://cdn.btree.com/categories/smartphones.jpg",
  "sort_order": 1
}
```

> `parent_id`, `description` e `image_url` são opcionais. `sort_order` defaults to `0`.

### Response (201 Created)

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

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug de categoria já está em uso"],
  "timestamp": "2026-04-10T14:00:00Z",
  "path": "/api/v1/catalog/categories"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> O banco de dados já está migrado (`V005__create_catalog_tables.sql`). O domínio e a infraestrutura já estão implementados. Foco total em `application` e `api`.

1. **`CategoryError`** — adicionar constante `PARENT_CATEGORY_NOT_FOUND`.
2. **`CreateCategoryCommand`** — record de entrada.
3. **`CreateCategoryOutput`** — record de saída com `from(Category)`.
4. **`CreateCategoryUseCase`** — lógica com `Either`, seguindo o algoritmo descrito acima.
5. **`@Bean` em `UseCaseConfig`** — wiring com `CategoryGateway`, `DomainEventPublisher`, `TransactionManager`.
6. **`CreateCategoryRequest`** — DTO de entrada com Bean Validation.
7. **`CreateCategoryResponse`** — DTO de saída com `from(CreateCategoryOutput)`.
8. **`CategoryController`** — `POST /api/v1/catalog/categories` retornando `201 Created`.
9. **Testes unitários** (`application/`) — `CreateCategoryUseCaseTest` com Mockito:
   - Cenário feliz — categoria raiz (sem parent)
   - Cenário feliz — categoria filha com parent válido
   - Slug já em uso → `Left` com `SLUG_ALREADY_EXISTS`
   - Parent não encontrado → `Left` com `PARENT_CATEGORY_NOT_FOUND`
   - Parent soft-deletado → `Left` com `PARENT_CATEGORY_NOT_FOUND`
   - Name inválido (vazio, muito longo) → `Left` com erros do `CategoryValidator`
   - Slug formato inválido → `Left` com `SLUG_INVALID_FORMAT`
