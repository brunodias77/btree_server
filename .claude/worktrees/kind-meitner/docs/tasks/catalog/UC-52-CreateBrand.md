# Task: UC-52 — CreateBrand

## 📋 Resumo

Permite que administradores cadastrem uma nova marca no catálogo. Uma marca (`Brand`) é um aggregate raiz com nome, slug único, descrição e URL do logo. Ao ser criada, emite o evento `BrandCreatedEvent` para processamento assíncrono downstream.

## 🎯 Objetivo

Expor o endpoint `POST /api/v1/catalog/brands` que valida unicidade do slug, cria o aggregate via `Brand.create()`, persiste via `gateway.save()` e publica o `BrandCreatedEvent`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/brands`
* **Tabelas do Banco:** `catalog.brands`

> ✅ **Estado atual do projeto**: toda a camada de `domain` e `infrastructure` já está implementada:
> - `Brand` aggregate, `BrandId`, `BrandGateway`, `BrandError`, `BrandValidator`, `BrandCreatedEvent` — todos prontos no `domain`.
> - `BrandJpaEntity`, `BrandJpaRepository`, `BrandPostgresGateway` — prontos no `infrastructure`.
> - Migration `V005__create_catalog_tables.sql` já criou a tabela `catalog.brands`.
>
> Esta task foca exclusivamente em:
> 1. Camada `application`: `CreateBrandCommand`, `CreateBrandOutput`, `CreateBrandUseCase`.
> 2. Camada `api`: `BrandController`, `CreateBrandRequest`, `CreateBrandResponse` e `@Bean` em `UseCaseConfig`.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
*Nenhuma alteração necessária.*

### `application`
1. `application/usecase/catalog/brand/CreateBrandCommand.java` — **criar**
2. `application/usecase/catalog/brand/CreateBrandOutput.java` — **criar**
3. `application/usecase/catalog/brand/CreateBrandUseCase.java` — **criar**

### `infrastructure`
*Nenhuma alteração necessária.*

### `api`
1. `api/catalog/BrandController.java` — **criar**
2. `api/catalog/CreateBrandRequest.java` — **criar**
3. `api/catalog/CreateBrandResponse.java` — **criar**
4. `api/config/UseCaseConfig.java` — ⚠️ **Alterar** — adicionar `@Bean createBrandUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Aggregate de Referência (Domain — já implementado)

O `Brand` aggregate expõe:

```java
// Campos: name, slug, description (nullable), logoUrl (nullable)
// Sem: parentId, sortOrder, active (sempre criado ativo — sem campo booleano explícito)

public static Brand create(
        String name,
        String slug,
        String description,
        String logoUrl,
        Notification notification   // acumula erros via BrandValidator
)

public static Brand with(
        BrandId id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
)
```

`Brand.create()` invoca `BrandValidator` e registra `BrandCreatedEvent`. Como pode lançar `DomainException` ao falhar validação interna, **deve ser chamado dentro do bloco `Try()`** no use case.

`BrandGateway` relevante para este use case:
```java
Brand save(Brand brand);
boolean existsBySlug(String slug);
```

### 2. Contrato de Entrada/Saída (Application)

**`CreateBrandCommand`**:

```java
public record CreateBrandCommand(
        String name,
        String slug,
        String description,  // nullable
        String logoUrl       // nullable
) {}
```

**`CreateBrandOutput`**:

```java
public record CreateBrandOutput(
        String  id,
        String  name,
        String  slug,
        String  description,
        String  logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static CreateBrandOutput from(final Brand brand) { ... }
}
```

> ℹ️ `Brand` não possui campo `active` nem `parentId`. O output não os inclui.

### 3. Lógica do Use Case (Application)

```java
public class CreateBrandUseCase implements UseCase<CreateBrandCommand, CreateBrandOutput> {

    private final BrandGateway brandGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    // construtor...

    @Override
    public Either<Notification, CreateBrandOutput> execute(final CreateBrandCommand command) {
        final var notification = Notification.create();

        // 1. Unicidade do slug (entre marcas não soft-deletadas)
        if (command.slug() != null && brandGateway.existsBySlug(command.slug())) {
            notification.append(BrandError.SLUG_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 2. Criar aggregate — Brand.create() valida via BrandValidator e pode lançar
        //    DomainException; Try captura e converte para Left(Notification).
        return Try(() -> transactionManager.execute(() -> {
            final var brand = Brand.create(
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.logoUrl(),
                    notification
            );
            final var saved = brandGateway.save(brand);
            eventPublisher.publishAll(brand.getDomainEvents());
            return CreateBrandOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> ⚠️ **Sobre `Brand.create()` e Notification**: diferente de um simples record, o aggregate acumula erros de validação no `Notification` passado por parâmetro e pode lançar `DomainException` se a validação falhar internamente. Por isso `create()` é chamado **dentro** do `Try`, garantindo que qualquer exceção seja capturada e convertida em `Left`.

### 4. Roteamento e Injeção (API)

**`CreateBrandRequest`** — validações Bean Validation:

```java
public record CreateBrandRequest(
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

        @JsonProperty("logo_url")
        @Size(max = 512, message = "'logo_url' deve ter no máximo 512 caracteres")
        String logoUrl
) {}
```

**`CreateBrandResponse`**:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBrandResponse(
        String  id,
        String  name,
        String  slug,
        String  description,
        @JsonProperty("logo_url")   String  logoUrl,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static CreateBrandResponse from(final CreateBrandOutput output) { ... }
}
```

**`BrandController`**:

```java
@RestController
@RequestMapping("/api/v1/catalog/brands")
@Tag(name = "Brands", description = "Gerenciamento de marcas do catálogo de produtos")
@SecurityRequirement(name = "bearerAuth")
public class BrandController {

    private final CreateBrandUseCase createBrandUseCase;

    public BrandController(final CreateBrandUseCase createBrandUseCase) {
        this.createBrandUseCase = createBrandUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Criar marca",
            description = "Cadastra uma nova marca no catálogo. " +
                          "O slug deve ser único entre marcas ativas e seguir o formato kebab-case " +
                          "(letras minúsculas, números e hífens)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Marca criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de slug, campos obrigatórios)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Slug já em uso")
    })
    public CreateBrandResponse create(@Valid @RequestBody final CreateBrandRequest request) {
        return CreateBrandResponse.from(
                createBrandUseCase.execute(new CreateBrandCommand(
                        request.name(),
                        request.slug(),
                        request.description(),
                        request.logoUrl()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

**`UseCaseConfig.java`** — adicionar na seção `Catalog`:

```java
@Bean
public CreateBrandUseCase createBrandUseCase(
        final BrandGateway brandGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new CreateBrandUseCase(brandGateway, eventPublisher, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `BrandError.SLUG_ALREADY_EXISTS` | Slug já cadastrado em marca ativa | `422 Unprocessable Entity` |
| `BrandError.NAME_EMPTY` | `name` nulo ou em branco (validação no aggregate) | `422 Unprocessable Entity` |
| `BrandError.NAME_TOO_LONG` | `name` > 200 caracteres | `422 Unprocessable Entity` |
| `BrandError.SLUG_EMPTY` | `slug` nulo ou em branco | `422 Unprocessable Entity` |
| `BrandError.SLUG_TOO_LONG` | `slug` > 256 caracteres | `422 Unprocessable Entity` |
| `BrandError.SLUG_INVALID_FORMAT` | `slug` fora do padrão kebab-case | `422 Unprocessable Entity` |
| `MethodArgumentNotValidException` (Bean Validation) | `@NotBlank`, `@Size`, `@Pattern` | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/brands`

```json
{
  "name": "Apple",
  "slug": "apple",
  "description": "Tecnologia, design e inovação",
  "logo_url": "https://cdn.btree.com/brands/apple.svg"
}
```

> `description` e `logo_url` são opcionais (nullable).

### Response (201 Created)

```json
{
  "id": "019600ab-dead-7000-a000-000000000010",
  "name": "Apple",
  "slug": "apple",
  "description": "Tecnologia, design e inovação",
  "logo_url": "https://cdn.btree.com/brands/apple.svg",
  "created_at": "2026-04-10T14:00:00Z",
  "updated_at": "2026-04-10T14:00:00Z"
}
```

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug de marca já está em uso"],
  "timestamp": "2026-04-10T14:00:00Z",
  "path": "/api/v1/catalog/brands"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> Domain e infrastructure já estão prontos. Foco nas camadas `application` e `api`.

1. **`CreateBrandCommand`** — record com `name`, `slug`, `description`, `logoUrl`.
2. **`CreateBrandOutput`** — record com factory `from(Brand)`.
3. **`CreateBrandUseCase`** — lógica com `Either`, checagem de slug e `BrandCreatedEvent`.
4. **`@Bean` em `UseCaseConfig`** — wiring com `BrandGateway`, `DomainEventPublisher` e `TransactionManager`.
5. **`CreateBrandRequest`** — Bean Validation (mesmo padrão do `CreateCategoryRequest`).
6. **`CreateBrandResponse`** — DTO com factory `from(CreateBrandOutput)`.
7. **`BrandController`** — endpoint `POST /` com `CreateBrandUseCase`.
8. **Testes unitários** (`application/`) — `CreateBrandUseCaseTest` com Mockito:
   - Cenário feliz — marca criada com sucesso, evento publicado
   - Cenário feliz — marca sem `description` e sem `logoUrl` (`null`)
   - Slug já existente → `Left` com `SLUG_ALREADY_EXISTS`
   - `Brand.create()` lança `DomainException` (name vazio) → `Left` capturado pelo `Try`
