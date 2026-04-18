# Task: UC-53 — UpdateBrand

## 📋 Resumo

Permite que administradores editem os dados de uma marca existente: nome, slug, descrição e URL do logo. A operação é um PUT semântico — o cliente envia o payload completo e todos os campos mutáveis são substituídos. Marcas soft-deletadas não podem ser editadas.

## 🎯 Objetivo

Expor o endpoint `PUT /api/v1/catalog/brands/{id}` que carrega a marca, valida unicidade do novo slug (excluindo a própria marca), aplica o método `update()` do aggregate e persiste via `gateway.update()`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (lógica), `api` (exposição HTTP)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `PUT /api/v1/catalog/brands/{id}`
* **Tabelas do Banco:** `catalog.brands`

> ⚠️ **Estado atual do projeto**: o `Brand` aggregate já possui o método `update()` implementado. O `BrandGateway` já possui `update(Brand)`. A camada de infraestrutura (`BrandJpaEntity`, `BrandPostgresGateway`) já está completa. Esta task foca em:
> 1. Adicionar `existsBySlugExcluding` no gateway e repositório.
> 2. Implementar `application` e `api` completos.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
| Arquivo | Ação |
|---|---|
| `domain/catalog/gateway/BrandGateway.java` | ⚠️ **Alterar** — adicionar `existsBySlugExcluding(String slug, BrandId excludeId)` |

### `application`
1. `application/usecase/catalog/brand/UpdateBrandCommand.java` — **criar**
2. `application/usecase/catalog/brand/UpdateBrandOutput.java` — **criar**
3. `application/usecase/catalog/brand/UpdateBrandUseCase.java` — **criar**

### `infrastructure`
| Arquivo | Ação |
|---|---|
| `infrastructure/catalog/persistence/BrandJpaRepository.java` | ⚠️ **Alterar** — adicionar `existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id)` |
| `infrastructure/catalog/persistence/BrandPostgresGateway.java` | ⚠️ **Alterar** — implementar `existsBySlugExcluding` |

### `api`
1. `api/catalog/UpdateBrandRequest.java` — **criar**
2. `api/catalog/UpdateBrandResponse.java` — **criar**
3. `api/catalog/BrandController.java` — ⚠️ **Alterar** — adicionar `PUT /{id}` e `UpdateBrandUseCase` ao construtor
4. `api/config/UseCaseConfig.java` — ⚠️ **Alterar** — adicionar `@Bean updateBrandUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway — novo método de unicidade (Domain)

O método `existsBySlug` existente não serve para update, pois retornaria `true` quando o slug informado é o mesmo da própria marca. Adicionar ao `BrandGateway`:

```java
/**
 * Verifica se existe marca ativa com o slug informado,
 * excluindo a marca com o ID especificado.
 * Usado em atualizações para permitir manter o mesmo slug.
 */
boolean existsBySlugExcluding(String slug, BrandId excludeId);
```

### 2. Repositório (Infrastructure)

Adicionar ao `BrandJpaRepository` o método derivado do Spring Data:

```java
boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id);
```

### 3. Gateway concreto (Infrastructure)

Implementar em `BrandPostgresGateway`:

```java
@Override
@Transactional(readOnly = true)
public boolean existsBySlugExcluding(final String slug, final BrandId excludeId) {
    return brandJpaRepository
            .existsBySlugAndDeletedAtIsNullAndIdNot(slug, excludeId.getValue());
}
```

### 4. Contrato de Entrada/Saída (Application)

**`UpdateBrandCommand`** — payload completo (PUT semântico):

```java
public record UpdateBrandCommand(
        String brandId,      // ID da marca a editar (da URL)
        String name,
        String slug,
        String description,  // nullable
        String logoUrl       // nullable
) {}
```

**`UpdateBrandOutput`** — mesmo shape do `CreateBrandOutput`:

```java
public record UpdateBrandOutput(
        String  id,
        String  name,
        String  slug,
        String  description,
        String  logoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static UpdateBrandOutput from(final Brand brand) { ... }
}
```

### 5. Lógica do Use Case (Application)

```java
public class UpdateBrandUseCase implements UseCase<UpdateBrandCommand, UpdateBrandOutput> {

    private final BrandGateway brandGateway;
    private final TransactionManager transactionManager;

    // construtor...

    @Override
    public Either<Notification, UpdateBrandOutput> execute(final UpdateBrandCommand command) {
        final var notification = Notification.create();

        // 1. Resolver e validar o ID da marca
        final BrandId brandId;
        try {
            brandId = BrandId.from(UUID.fromString(command.brandId()));
        } catch (IllegalArgumentException e) {
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }

        // 2. Carregar a marca — retorna Left se não existir
        final var brandOpt = brandGateway.findById(brandId);
        if (brandOpt.isEmpty()) {
            notification.append(BrandError.BRAND_NOT_FOUND);
            return Left(notification);
        }

        final var brand = brandOpt.get();

        // 3. Rejeitar marcas soft-deletadas
        if (brand.isDeleted()) {
            notification.append(BrandError.BRAND_ALREADY_DELETED);
            return Left(notification);
        }

        // 4. Unicidade do slug — excluindo a própria marca
        if (command.slug() != null
                && !command.slug().equals(brand.getSlug())
                && brandGateway.existsBySlugExcluding(command.slug(), brandId)) {
            notification.append(BrandError.SLUG_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 5. Aplicar mutação no aggregate e persistir
        return Try(() -> transactionManager.execute(() -> {
            brand.update(
                    command.name(),
                    command.slug(),
                    command.description(),
                    command.logoUrl()
            );
            final var updated = brandGateway.update(brand);
            return UpdateBrandOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> ⚠️ **Sobre `Brand.update()`**: diferente de `Brand.create()`, o método `update()` do aggregate **não valida** internamente. A validação de campos (name vazio, slug inválido) é delegada ao Bean Validation do `UpdateBrandRequest` na camada `api`. O método apenas aplica os valores e atualiza `updatedAt`.

> ⚠️ **Sobre slug igual**: a checagem de unicidade é feita apenas quando o slug muda (`!command.slug().equals(brand.getSlug())`). Evita falso positivo quando o cliente reenvia o mesmo slug.

### 6. Roteamento e Injeção (API)

**`UpdateBrandRequest`** — mesmas validações do `CreateBrandRequest`:

```java
public record UpdateBrandRequest(
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

**`UpdateBrandResponse`** — mesmo shape do `CreateBrandResponse`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateBrandResponse(
        String  id,
        String  name,
        String  slug,
        String  description,
        @JsonProperty("logo_url")   String  logoUrl,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static UpdateBrandResponse from(final UpdateBrandOutput output) { ... }
}
```

**`BrandController`** — adicionar `UpdateBrandUseCase` ao construtor e novo endpoint:

```java
@PutMapping("/{id}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Editar marca",
        description = "Atualiza todos os campos mutáveis de uma marca existente (PUT semântico). " +
                      "Campos não enviados são gravados como null. " +
                      "Não é possível editar uma marca removida."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marca atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "422", description = "Marca não encontrada, deletada ou slug já em uso")
})
public UpdateBrandResponse update(
        @PathVariable final String id,
        @Valid @RequestBody final UpdateBrandRequest request
) {
    return UpdateBrandResponse.from(
            updateBrandUseCase.execute(new UpdateBrandCommand(
                    id,
                    request.name(),
                    request.slug(),
                    request.description(),
                    request.logoUrl()
            )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar após `createBrandUseCase`:

```java
@Bean
public UpdateBrandUseCase updateBrandUseCase(
        final BrandGateway brandGateway,
        final TransactionManager transactionManager
) {
    return new UpdateBrandUseCase(brandGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `BrandError.BRAND_NOT_FOUND` | ID não existe na base ou UUID inválido | `422 Unprocessable Entity` |
| `BrandError.BRAND_ALREADY_DELETED` | Marca com `deleted_at != null` | `422 Unprocessable Entity` |
| `BrandError.SLUG_ALREADY_EXISTS` | Novo slug já usado por outra marca ativa | `422 Unprocessable Entity` |
| `MethodArgumentNotValidException` (Bean Validation) | `@NotBlank`, `@Size`, `@Pattern` | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### Request — `PUT /api/v1/catalog/brands/{id}`

```json
{
  "name": "Samsung Electronics",
  "slug": "samsung-electronics",
  "description": "Líder global em tecnologia e inovação",
  "logo_url": "https://cdn.btree.com/brands/samsung-v2.svg"
}
```

> `description` e `logo_url` são opcionais (nullable). Omiti-los grava `null`.

### Response (200 OK)

```json
{
  "id": "019600ab-dead-7000-a000-000000000010",
  "name": "Samsung Electronics",
  "slug": "samsung-electronics",
  "description": "Líder global em tecnologia e inovação",
  "logo_url": "https://cdn.btree.com/brands/samsung-v2.svg",
  "created_at": "2026-04-10T14:00:00Z",
  "updated_at": "2026-04-10T15:30:00Z"
}
```

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug de marca já está em uso"],
  "timestamp": "2026-04-10T15:30:00Z",
  "path": "/api/v1/catalog/brands/019600ab-dead-7000-a000-000000000010"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> Domain, infrastructure e application base já estão prontos. Foco nas extensões e camadas de application e api.

1. **`BrandGateway`** — adicionar `existsBySlugExcluding(String slug, BrandId excludeId)`.
2. **`BrandJpaRepository`** — adicionar `existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id)`.
3. **`BrandPostgresGateway`** — implementar `existsBySlugExcluding` com `@Transactional(readOnly = true)`.
4. **`UpdateBrandCommand`** — record de entrada.
5. **`UpdateBrandOutput`** — record de saída com `from(Brand)`.
6. **`UpdateBrandUseCase`** — lógica completa com `Either`.
7. **`@Bean` em `UseCaseConfig`** — wiring com `BrandGateway` e `TransactionManager`.
8. **`UpdateBrandRequest`** — Bean Validation (mesmo padrão do `CreateBrandRequest`).
9. **`UpdateBrandResponse`** — DTO com `from(UpdateBrandOutput)`.
10. **`BrandController`** — adicionar `UpdateBrandUseCase` ao construtor e endpoint `PUT /{id}`.
11. **Testes unitários** (`application/`) — `UpdateBrandUseCaseTest` com Mockito:
    - Cenário feliz — atualização completa com novos valores
    - Cenário feliz — manter mesmo slug (sem falso positivo de unicidade)
    - Cenário feliz — remover `description` e `logoUrl` (passar `null`)
    - Marca não encontrada → `Left` com `BRAND_NOT_FOUND`
    - UUID inválido → `Left` com `BRAND_NOT_FOUND`
    - Marca soft-deletada → `Left` com `BRAND_ALREADY_DELETED`
    - Novo slug em uso por outra marca → `Left` com `SLUG_ALREADY_EXISTS`
