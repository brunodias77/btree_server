# Task: UC-57 — CreateProduct

## 📋 Resumo

Permite cadastrar um novo produto no catálogo com status inicial `DRAFT`. O produto é criado com os dados básicos (nome, SKU, preço, dimensões, etc.) e, opcionalmente, com uma lista de imagens. Por estar em `DRAFT`, o produto não é visível ao consumidor final até que seja publicado via `PublishProduct` (UC-58). O evento `ProductCreatedEvent` é disparado para notificar outros bounded contexts (ex.: indexação de busca).

## 🎯 Objetivo

Implementar o endpoint `POST /api/v1/catalog/products` que receba os dados de um novo produto, execute as validações de unicidade (slug e SKU), persista o aggregate `Product` em `DRAFT` junto com suas imagens iniciais, e retorne a representação completa do produto criado.

## 📦 Contexto Técnico

* **Módulo Principal:** `application` (orquestra domain + infra já existentes)
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/catalog/products`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`

> ⚠️ **Importante:** As camadas `domain` e `infrastructure` para `Product` **já estão completamente implementadas**. Não é necessário criar `Product.java`, `ProductGateway.java`, `ProductJpaEntity.java`, `ProductImageJpaEntity.java`, `ProductPostgresGateway.java` nem migrations. O trabalho deste UC está concentrado em `application/` e `api/`.

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
> Nenhum arquivo novo — tudo já existe.
>
> Referência dos artefatos utilizados:
> - `com.btree.domain.catalog.entity.Product` — Aggregate (já implementado)
> - `com.btree.domain.catalog.entity.ProductImage` — Entity filha (já implementada)
> - `com.btree.domain.catalog.gateway.ProductGateway` — Interface de porta (já implementada)
> - `com.btree.domain.catalog.error.ProductError` — Constantes de erro (já implementadas)
> - `com.btree.domain.catalog.valueobject.ProductDimensions` — Value Object (já implementado)
> - `com.btree.domain.catalog.identifier.{CategoryId, BrandId, ProductId}` — IDs tipados (já implementados)
> - `com.btree.domain.catalog.events.ProductCreatedEvent` — Domain Event (já implementado)

### `application`
1. **CRIAR** `modules/application/src/main/java/com/btree/application/usecase/catalog/product/CreateProductCommand.java`
2. **CRIAR** `modules/application/src/main/java/com/btree/application/usecase/catalog/product/CreateProductOutput.java`
3. **CRIAR** `modules/application/src/main/java/com/btree/application/usecase/catalog/product/CreateProductUseCase.java`

### `infrastructure`
> Nenhum arquivo novo — `ProductPostgresGateway`, `ProductJpaEntity` e `ProductJpaRepository` já estão implementados.

### `api`
1. **CRIAR** `modules/api/src/main/java/com/btree/api/catalog/CreateProductRequest.java`
2. **CRIAR** `modules/api/src/main/java/com/btree/api/catalog/ProductImageRequest.java` — DTO de imagem aninhado no request
3. **CRIAR** `modules/api/src/main/java/com/btree/api/catalog/CreateProductResponse.java`
4. **CRIAR** `modules/api/src/main/java/com/btree/api/catalog/ProductController.java`
5. **ALTERAR** `modules/api/src/main/java/com/btree/api/config/UseCaseConfig.java` — adicionar `@Bean createProductUseCase`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. `CreateProductCommand` (Application)

Record imutável com todos os dados primitivos de entrada. As imagens são representadas como uma lista de `ImageEntry`, que é um record interno:

```java
package com.btree.application.usecase.catalog.product;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductCommand(
        String categoryId,        // UUID como String (nullable — produto sem categoria é válido em DRAFT)
        String brandId,           // UUID como String (nullable)
        String name,
        String slug,
        String description,       // nullable
        String shortDescription,  // nullable
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice, // nullable
        BigDecimal costPrice,      // nullable
        int lowStockThreshold,
        BigDecimal weight,         // nullable — mapeado para ProductDimensions
        BigDecimal width,          // nullable
        BigDecimal height,         // nullable
        BigDecimal depth,          // nullable
        List<ImageEntry> images    // nullable ou lista vazia

) {
    public record ImageEntry(String url, String altText, int sortOrder, boolean primary) {}
}
```

### 2. `CreateProductOutput` (Application)

```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.shared.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateProductOutput(
        String id,
        String categoryId,
        String brandId,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        int quantity,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        ProductStatus status,
        boolean featured,
        List<ImageOutput> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record ImageOutput(String id, String url, String altText, int sortOrder, boolean primary) {
        public static ImageOutput from(final ProductImage image) {
            return new ImageOutput(
                    image.getId().getValue().toString(),
                    image.getUrl(),
                    image.getAltText(),
                    image.getSortOrder(),
                    image.isPrimary()
            );
        }
    }

    public static CreateProductOutput from(final Product product) {
        final var dims = product.getDimensions();
        return new CreateProductOutput(
                product.getId().getValue().toString(),
                product.getCategoryId() != null ? product.getCategoryId().getValue().toString() : null,
                product.getBrandId() != null ? product.getBrandId().getValue().toString() : null,
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getShortDescription(),
                product.getSku(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getCostPrice(),
                product.getQuantity(),
                product.getLowStockThreshold(),
                dims != null ? dims.getWeight() : null,
                dims != null ? dims.getWidth() : null,
                dims != null ? dims.getHeight() : null,
                dims != null ? dims.getDepth() : null,
                product.getStatus(),
                product.isFeatured(),
                product.getImages().stream().map(ImageOutput::from).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
```

### 3. `CreateProductUseCase` (Application)

> **Atenção:** `Product.create()` cria internamente sua própria `Notification`, valida e **lança `DomainException`** se houver erros (igual ao comportamento de `Brand.create()`). Por isso, deve ser chamado dentro de `Try()`.

```java
package com.btree.application.usecase.catalog.product;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.entity.ProductImage;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.valueobject.ProductDimensions;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.util.List;

import static io.vavr.control.Either.left;

public class CreateProductUseCase implements UseCase<CreateProductCommand, CreateProductOutput> {

    private final ProductGateway productGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public CreateProductUseCase(
            final ProductGateway productGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.productGateway = productGateway;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, CreateProductOutput> execute(final CreateProductCommand command) {
        final var notification = Notification.create();

        // 1. Validações de unicidade (acumular todos os erros antes de retornar)
        if (productGateway.existsBySlug(command.slug())) {
            notification.append(ProductError.SLUG_ALREADY_EXISTS);
        }
        if (productGateway.existsBySku(command.sku())) {
            notification.append(ProductError.SKU_ALREADY_EXISTS);
        }

        if (notification.hasError()) {
            return left(notification);
        }

        // 2. Resolver IDs opcionais
        final var categoryId = command.categoryId() != null
                ? CategoryId.from(command.categoryId()) : null;
        final var brandId = command.brandId() != null
                ? BrandId.from(command.brandId()) : null;

        // 3. Construir ProductDimensions (null-safe)
        final var dimensions = ProductDimensions.of(
                command.weight(), command.width(), command.height(), command.depth()
        );

        // 4. Criar aggregate + imagens dentro da transação
        //    Product.create() lança DomainException se validação falhar → Try captura
        return Try(() -> transactionManager.execute(() -> {
            final var product = Product.create(
                    categoryId, brandId,
                    command.name(), command.slug(),
                    command.description(), command.shortDescription(),
                    command.sku(),
                    command.price(), command.compareAtPrice(), command.costPrice(),
                    command.lowStockThreshold(),
                    dimensions
            );

            // Adicionar imagens iniciais (se fornecidas)
            if (command.images() != null) {
                final var images = resolveImages(command.images(), product);
                images.forEach(product::addImage);
            }

            final var saved = productGateway.save(product);
            eventPublisher.publishAll(product.getDomainEvents());
            return CreateProductOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }

    private List<ProductImage> resolveImages(
            final List<CreateProductCommand.ImageEntry> entries,
            final Product product
    ) {
        return entries.stream()
                .map(e -> ProductImage.create(
                        product.getId(),
                        e.url(),
                        e.altText(),
                        e.sortOrder(),
                        e.primary()
                ))
                .toList();
    }
}
```

### 4. `CreateProductRequest` (API)

```java
package com.btree.api.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(

        String categoryId,      // opcional em DRAFT

        String brandId,         // opcional em DRAFT

        @NotBlank @Size(max = 300)
        String name,

        @NotBlank @Size(max = 350)
        String slug,

        String description,

        @Size(max = 500)
        String shortDescription,

        @NotBlank @Size(max = 50)
        String sku,

        @NotNull @DecimalMin("0.00")
        BigDecimal price,

        @DecimalMin("0.00")
        BigDecimal compareAtPrice,

        @DecimalMin("0.00")
        BigDecimal costPrice,

        int lowStockThreshold,

        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,

        @Valid
        List<ProductImageRequest> images
) {}
```

### 5. `ProductImageRequest` (API)

```java
package com.btree.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(

        @NotBlank @Size(max = 512)
        String url,

        @Size(max = 256)
        String altText,

        int sortOrder,

        boolean primary
) {}
```

### 6. `CreateProductResponse` (API)

```java
package com.btree.api.catalog;

import com.btree.application.usecase.catalog.product.CreateProductOutput;
import com.btree.shared.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateProductResponse(
        String id,
        String categoryId,
        String brandId,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        int quantity,
        int lowStockThreshold,
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal depth,
        ProductStatus status,
        boolean featured,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record ImageResponse(String id, String url, String altText, int sortOrder, boolean primary) {}

    public static CreateProductResponse from(final CreateProductOutput output) {
        return new CreateProductResponse(
                output.id(),
                output.categoryId(),
                output.brandId(),
                output.name(),
                output.slug(),
                output.description(),
                output.shortDescription(),
                output.sku(),
                output.price(),
                output.compareAtPrice(),
                output.costPrice(),
                output.quantity(),
                output.lowStockThreshold(),
                output.weight(),
                output.width(),
                output.height(),
                output.depth(),
                output.status(),
                output.featured(),
                output.images().stream()
                        .map(i -> new ImageResponse(i.id(), i.url(), i.altText(), i.sortOrder(), i.primary()))
                        .toList(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}
```

### 7. `ProductController` (API)

```java
package com.btree.api.catalog;

import com.btree.application.usecase.catalog.product.CreateProductCommand;
import com.btree.application.usecase.catalog.product.CreateProductUseCase;
import com.btree.shared.exception.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/products")
@Tag(name = "Products", description = "Gerenciamento de produtos do catálogo")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;

    public ProductController(final CreateProductUseCase createProductUseCase) {
        this.createProductUseCase = createProductUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar produto", description = "Cria um novo produto em status DRAFT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Slug ou SKU já cadastrado"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public CreateProductResponse create(@Valid @RequestBody final CreateProductRequest request) {
        final var images = request.images() != null
                ? request.images().stream()
                        .map(i -> new CreateProductCommand.ImageEntry(i.url(), i.altText(), i.sortOrder(), i.primary()))
                        .toList()
                : List.<CreateProductCommand.ImageEntry>of();

        final var command = new CreateProductCommand(
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.slug(),
                request.description(),
                request.shortDescription(),
                request.sku(),
                request.price(),
                request.compareAtPrice(),
                request.costPrice(),
                request.lowStockThreshold(),
                request.weight(),
                request.width(),
                request.height(),
                request.depth(),
                images
        );

        return CreateProductResponse.from(
                createProductUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

### 8. `UseCaseConfig` — Bean a adicionar

Na seção `// ── Catalog ───`, após o `@Bean` de `GetCategoryUseCase`:

```java
@Bean
public CreateProductUseCase createProductUseCase(
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new CreateProductUseCase(productGateway, eventPublisher, transactionManager);
}
```

Também adicionar o import:
```java
import com.btree.application.usecase.catalog.product.CreateProductUseCase;
import com.btree.domain.catalog.gateway.ProductGateway;
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.SLUG_ALREADY_EXISTS` | `productGateway.existsBySlug(slug)` retorna `true` | `422 Unprocessable Entity` |
| `ProductError.SKU_ALREADY_EXISTS` | `productGateway.existsBySku(sku)` retorna `true` | `422 Unprocessable Entity` |
| `ProductError.NAME_EMPTY` | `name` nulo ou em branco | `422 Unprocessable Entity` |
| `ProductError.NAME_TOO_LONG` | `name` > 300 caracteres | `422 Unprocessable Entity` |
| `ProductError.SLUG_EMPTY` | `slug` nulo ou em branco | `422 Unprocessable Entity` |
| `ProductError.SLUG_TOO_LONG` | `slug` > 350 caracteres | `422 Unprocessable Entity` |
| `ProductError.SLUG_INVALID_FORMAT` | `slug` com caracteres inválidos (via `ProductValidator`) | `422 Unprocessable Entity` |
| `ProductError.SKU_EMPTY` | `sku` nulo ou em branco | `422 Unprocessable Entity` |
| `ProductError.SKU_TOO_LONG` | `sku` > 50 caracteres | `422 Unprocessable Entity` |
| `ProductError.PRICE_NULL` | `price` nulo | `422 Unprocessable Entity` |
| `ProductError.PRICE_NEGATIVE` | `price` < 0 | `422 Unprocessable Entity` |
| `ProductError.LOW_STOCK_THRESHOLD_NEGATIVE` | `lowStockThreshold` < 0 | `422 Unprocessable Entity` |
| `MethodArgumentNotValidException` (Bean Validation) | request com campos inválidos | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/catalog/products`

```json
{
  "categoryId": "019712a0-0000-7000-8000-000000000001",
  "brandId": "019712a0-0000-7000-8000-000000000002",
  "name": "Tênis Air Max 270",
  "slug": "tenis-air-max-270",
  "description": "Tênis de corrida com amortecimento Air Max.",
  "shortDescription": "Tênis Air Max com amortecimento revolucionário.",
  "sku": "NK-AM270-BLK-42",
  "price": 899.90,
  "compareAtPrice": 1099.90,
  "costPrice": 450.00,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 32.0,
  "height": 12.0,
  "depth": 20.0,
  "images": [
    {
      "url": "https://cdn.btree.com/products/tenis-am270-frente.jpg",
      "altText": "Tênis Air Max 270 - Vista frontal",
      "sortOrder": 0,
      "primary": true
    },
    {
      "url": "https://cdn.btree.com/products/tenis-am270-lateral.jpg",
      "altText": "Tênis Air Max 270 - Vista lateral",
      "sortOrder": 1,
      "primary": false
    }
  ]
}
```

> Campos opcionais: `categoryId`, `brandId`, `description`, `shortDescription`, `compareAtPrice`, `costPrice`, `weight`, `width`, `height`, `depth`, `images`.

### Response — `201 Created`

```json
{
  "id": "019712b0-0000-7000-8000-000000000010",
  "categoryId": "019712a0-0000-7000-8000-000000000001",
  "brandId": "019712a0-0000-7000-8000-000000000002",
  "name": "Tênis Air Max 270",
  "slug": "tenis-air-max-270",
  "description": "Tênis de corrida com amortecimento Air Max.",
  "shortDescription": "Tênis Air Max com amortecimento revolucionário.",
  "sku": "NK-AM270-BLK-42",
  "price": 899.90,
  "compareAtPrice": 1099.90,
  "costPrice": 450.00,
  "quantity": 0,
  "lowStockThreshold": 5,
  "weight": 0.320,
  "width": 32.0,
  "height": 12.0,
  "depth": 20.0,
  "status": "DRAFT",
  "featured": false,
  "images": [
    {
      "id": "019712b0-0000-7000-8000-000000000011",
      "url": "https://cdn.btree.com/products/tenis-am270-frente.jpg",
      "altText": "Tênis Air Max 270 - Vista frontal",
      "sortOrder": 0,
      "primary": true
    }
  ],
  "createdAt": "2026-04-10T14:00:00Z",
  "updatedAt": "2026-04-10T14:00:00Z"
}
```

### Response — `422 Unprocessable Entity`

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Slug já está em uso", "SKU já está em uso"],
  "timestamp": "2026-04-10T14:00:00Z",
  "path": "/api/v1/catalog/products"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> As camadas `domain` e `infrastructure` já estão implementadas. Pular direto para `application`:

1. `CreateProductCommand.java` — record com `ImageEntry` interno.
2. `CreateProductOutput.java` — record com `ImageOutput` interno e factory `from(Product)`.
3. `CreateProductUseCase.java` — lógica com `Either`, chamada a `Product.create()` dentro de `Try()`.
4. `@Bean createProductUseCase` em `UseCaseConfig.java` — adicionar import de `ProductGateway`.
5. `ProductImageRequest.java` — DTO de imagem aninhado.
6. `CreateProductRequest.java` — Bean Validation com `@Valid List<ProductImageRequest>`.
7. `CreateProductResponse.java` — record com `ImageResponse` interno e factory `from(Output)`.
8. `ProductController.java` — `POST /api/v1/catalog/products`, converte request → command → response.
9. Testes unitários em `application/` — `CreateProductUseCaseTest.java` (JUnit 5 + Mockito, sem Spring):
   - Cenário feliz: produto criado com imagens → `Right` com `CreateProductOutput`
   - Slug duplicado → `Left` com erro `SLUG_ALREADY_EXISTS`
   - SKU duplicado → `Left` com erro `SKU_ALREADY_EXISTS`
   - Ambos duplicados → `Left` com 2 erros acumulados
   - Dados inválidos (name em branco, price negativo) → `Left` via `DomainException` capturado pelo `Try`
