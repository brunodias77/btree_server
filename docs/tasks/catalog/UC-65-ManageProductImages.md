# Task: UC-65 — ManageProductImages

## 📋 Resumo
Agrupa as quatro operações de gestão de imagens de produto: **adicionar**, **remover**, **definir primária** e **reordenar**. Imagens são entidades dentro do aggregate `Product` (tabela `catalog.product_images`), gerenciadas exclusivamente via métodos de ciclo de vida no aggregate. Cada operação emite um Domain Event correspondente para rastreabilidade. A URL da imagem é fornecida pelo cliente — o upload físico para storage (MinIO) deve ser feito previamente via endpoint dedicado de mídia (fora do escopo deste UC).

## 🎯 Objetivo
Ao receber as chamadas abaixo, o sistema deve:

| Operação | Endpoint | Comportamento |
|---|---|---|
| Adicionar imagem | `POST /api/v1/catalog/products/{productId}/images` | Valida limite de imagens (máx. 10), unicidade de URL por produto, adiciona ao aggregate e persiste |
| Remover imagem | `DELETE /api/v1/catalog/products/{productId}/images/{imageId}` | Localiza e remove a imagem do aggregate; se era primária, nenhuma outra é promovida automaticamente |
| Definir primária | `PATCH /api/v1/catalog/products/{productId}/images/{imageId}/primary` | Desmarca todas as outras como não-primárias e marca a imagem alvo |
| Reordenar | `PUT /api/v1/catalog/products/{productId}/images/reorder` | Recebe lista ordenada de IDs e atualiza `sort_order` de cada imagem sequencialmente |

## 📦 Contexto Técnico
* **Módulo Principal:** `domain` (ciclo de vida), `application` (use cases), `infrastructure` (persistência), `api` (roteamento)
* **Prioridade:** `ALTA`
* **Endpoints:**
  * `POST /api/v1/catalog/products/{productId}/images`
  * `DELETE /api/v1/catalog/products/{productId}/images/{imageId}`
  * `PATCH /api/v1/catalog/products/{productId}/images/{imageId}/primary`
  * `PUT /api/v1/catalog/products/{productId}/images/reorder`
* **Tabelas do Banco:** `catalog.products`, `catalog.product_images`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. **ALTERAR** `domain/.../catalog/entity/Product.java` — adicionar métodos `addImage()`, `removeImage()`, `setPrimaryImage()`, `reorderImages()`, todos aceitando `Notification`.
2. **ALTERAR** `domain/.../catalog/error/ProductError.java` — adicionar `PRODUCT_IMAGE_NOT_FOUND`, `PRODUCT_IMAGE_URL_ALREADY_EXISTS`, `PRODUCT_IMAGE_LIMIT_EXCEEDED`.
3. **CRIAR** `domain/.../catalog/events/ProductImageAddedEvent.java` — Domain Event para adição.
4. **CRIAR** `domain/.../catalog/events/ProductImageRemovedEvent.java` — Domain Event para remoção.

### `application`
1. **CRIAR** `application/.../catalog/product/AddProductImageCommand.java`
2. **CRIAR** `application/.../catalog/product/AddProductImageOutput.java`
3. **CRIAR** `application/.../catalog/product/AddProductImageUseCase.java`
4. **CRIAR** `application/.../catalog/product/RemoveProductImageCommand.java`
5. **CRIAR** `application/.../catalog/product/RemoveProductImageUseCase.java` — `UnitUseCase` (retorna `Either<Notification, Void>`)
6. **CRIAR** `application/.../catalog/product/SetPrimaryProductImageCommand.java`
7. **CRIAR** `application/.../catalog/product/SetPrimaryProductImageOutput.java`
8. **CRIAR** `application/.../catalog/product/SetPrimaryProductImageUseCase.java`
9. **CRIAR** `application/.../catalog/product/ReorderProductImagesCommand.java`
10. **CRIAR** `application/.../catalog/product/ReorderProductImagesOutput.java`
11. **CRIAR** `application/.../catalog/product/ReorderProductImagesUseCase.java`

### `infrastructure`
Nenhum arquivo novo — `ProductPostgresGateway.update()` já persiste o aggregate inteiro incluindo a coleção `images` via cascade.

> Confirmar que `ProductJpaEntity` usa `CascadeType.PERSIST, MERGE` na coleção de imagens e que `ProductImageJpaEntity` tem `orphanRemoval = true` para que remoções no aggregate se reflitam no banco.

### `api`
1. **CRIAR** `api/.../catalog/AddProductImageRequest.java`
2. **CRIAR** `api/.../catalog/AddProductImageResponse.java`
3. **CRIAR** `api/.../catalog/ReorderProductImagesRequest.java`
4. **CRIAR** `api/.../catalog/SetPrimaryProductImageResponse.java`
5. **CRIAR** `api/.../catalog/ReorderProductImagesResponse.java`
6. **ALTERAR** `api/.../catalog/ProductController.java` — adicionar os 4 endpoints.
7. **ALTERAR** `api/.../config/UseCaseConfig.java` — registrar os 4 `@Bean`s.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Métodos de Ciclo de Vida no Aggregate (Domain)

Todos os métodos recebem `Notification` e acumulam erros sem mutar estado em caso de falha.

```java
private static final int MAX_IMAGES = 10;

/** Adiciona uma nova imagem ao produto. */
public void addImage(final ProductImage image, final Notification notification) {
    if (this.deletedAt != null) {
        notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
        return;
    }
    if (this.images.size() >= MAX_IMAGES) {
        notification.append(ProductError.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        return;
    }
    final boolean urlAlreadyExists = this.images.stream()
            .anyMatch(img -> img.getUrl().equalsIgnoreCase(image.getUrl()));
    if (urlAlreadyExists) {
        notification.append(ProductError.PRODUCT_IMAGE_URL_ALREADY_EXISTS);
        return;
    }
    this.images.add(image);
    this.updatedAt = Instant.now();
    registerEvent(new ProductImageAddedEvent(getId().getValue().toString(), image.getId().getValue().toString()));
}

/** Remove uma imagem pelo ID. */
public void removeImage(final ProductImageId imageId, final Notification notification) {
    final var image = this.images.stream()
            .filter(img -> img.getId().equals(imageId))
            .findFirst()
            .orElse(null);
    if (image == null) {
        notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
        return;
    }
    this.images.remove(image);
    this.updatedAt = Instant.now();
    registerEvent(new ProductImageRemovedEvent(getId().getValue().toString(), imageId.getValue().toString()));
}

/** Define uma imagem como primária, desmarcando as demais. */
public void setPrimaryImage(final ProductImageId imageId, final Notification notification) {
    final boolean exists = this.images.stream()
            .anyMatch(img -> img.getId().equals(imageId));
    if (!exists) {
        notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
        return;
    }
    this.images.forEach(img -> img.setPrimary(img.getId().equals(imageId)));
    this.updatedAt = Instant.now();
}

/**
 * Reordena as imagens de acordo com a lista de IDs fornecida.
 * A posição na lista define o novo sortOrder (base 1).
 */
public void reorderImages(final List<ProductImageId> orderedIds, final Notification notification) {
    if (orderedIds.size() != this.images.size()) {
        notification.append(ProductError.PRODUCT_IMAGE_REORDER_INCOMPLETE);
        return;
    }
    for (int i = 0; i < orderedIds.size(); i++) {
        final var id = orderedIds.get(i);
        final var image = this.images.stream()
                .filter(img -> img.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (image == null) {
            notification.append(ProductError.PRODUCT_IMAGE_NOT_FOUND);
            return;
        }
        image.setSortOrder(i + 1);
    }
    this.updatedAt = Instant.now();
}
```

**Erros em `ProductError`** — adicionar:
```java
public static final Error PRODUCT_IMAGE_NOT_FOUND =
        new Error("Imagem não encontrada neste produto.");

public static final Error PRODUCT_IMAGE_URL_ALREADY_EXISTS =
        new Error("Já existe uma imagem com esta URL neste produto.");

public static final Error PRODUCT_IMAGE_LIMIT_EXCEEDED =
        new Error("O produto já atingiu o limite máximo de 10 imagens.");

public static final Error PRODUCT_IMAGE_REORDER_INCOMPLETE =
        new Error("A lista de reordenação deve conter exatamente todas as imagens do produto.");

public static final Error CANNOT_MODIFY_DELETED_PRODUCT =
        new Error("Não é possível modificar um produto deletado.");
```

**Domain Events**:
```java
// ProductImageAddedEvent.java
public class ProductImageAddedEvent extends DomainEvent {
    private final String productId;
    private final String imageId;
    // aggregateType = "Product", eventType = "product.image.added"
}

// ProductImageRemovedEvent.java
public class ProductImageRemovedEvent extends DomainEvent {
    private final String productId;
    private final String imageId;
    // aggregateType = "Product", eventType = "product.image.removed"
}
```

### 2. Contrato de Entrada/Saída (Application)

**`AddProductImageCommand`**:
```java
public record AddProductImageCommand(
        String productId,
        String url,
        String altText,
        int sortOrder,
        boolean primary
) {}
```

**`AddProductImageOutput`** — snapshot completo do produto atualizado (inclui todas as imagens):
```java
public record AddProductImageOutput(
        String productId,
        List<ImageOutput> images
) {
    public record ImageOutput(String id, String url, String altText, int sortOrder, boolean primary) {
        public static ImageOutput from(final ProductImage image) { ... }
    }
    public static AddProductImageOutput from(final Product product) { ... }
}
```

**`RemoveProductImageCommand`**:
```java
public record RemoveProductImageCommand(String productId, String imageId) {}
```

> `RemoveProductImageUseCase` implementa `UnitUseCase<RemoveProductImageCommand>` — retorna `Either<Notification, Void>`.

**`SetPrimaryProductImageCommand`**:
```java
public record SetPrimaryProductImageCommand(String productId, String imageId) {}
```

**`SetPrimaryProductImageOutput`** — lista de imagens após a operação:
```java
public record SetPrimaryProductImageOutput(
        String productId,
        List<ImageOutput> images
) { ... }
```

**`ReorderProductImagesCommand`**:
```java
public record ReorderProductImagesCommand(
        String productId,
        List<String> imageIds   // IDs na nova ordem desejada (base 1)
) {}
```

**`ReorderProductImagesOutput`**:
```java
public record ReorderProductImagesOutput(
        String productId,
        List<ImageOutput> images   // imagens com novos sortOrders
) { ... }
```

### 3. Lógica dos Use Cases (Application)

**`AddProductImageUseCase`**:
```java
@Override
public Either<Notification, AddProductImageOutput> execute(final AddProductImageCommand command) {
    final var notification = Notification.create();

    // 1. Carregar produto (pré-condição)
    final var product = productGateway.findById(ProductId.from(command.productId()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    // 2. Construir ProductImage com novo ID
    final var image = ProductImage.create(
            ProductImageId.unique(),
            command.url(),
            command.altText(),
            command.sortOrder(),
            command.primary()
    );

    // 3. Aplicar ao aggregate (valida limite, URL duplicada, deletado)
    product.addImage(image, notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    // 4. Se nova imagem é primária, desmarcar as outras
    if (command.primary()) {
        product.setPrimaryImage(image.getId(), notification);
        if (notification.hasError()) {
            return Left(notification);
        }
    }

    // 5. Persistir
    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return AddProductImageOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

**`RemoveProductImageUseCase`**:
```java
@Override
public Either<Notification, Void> execute(final RemoveProductImageCommand command) {
    final var notification = Notification.create();

    final var product = productGateway.findById(ProductId.from(command.productId()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    product.removeImage(ProductImageId.from(command.imageId()), notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    return Try(() -> transactionManager.execute(() -> {
        productGateway.update(product);
        eventPublisher.publishAll(product.getDomainEvents());
        return null;
    })).toEither().mapLeft(Notification::create);
}
```

**`SetPrimaryProductImageUseCase`**:
```java
@Override
public Either<Notification, SetPrimaryProductImageOutput> execute(
        final SetPrimaryProductImageCommand command) {
    final var notification = Notification.create();

    final var product = productGateway.findById(ProductId.from(command.productId()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    product.setPrimaryImage(ProductImageId.from(command.imageId()), notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        return SetPrimaryProductImageOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

**`ReorderProductImagesUseCase`**:
```java
@Override
public Either<Notification, ReorderProductImagesOutput> execute(
        final ReorderProductImagesCommand command) {
    final var notification = Notification.create();

    final var product = productGateway.findById(ProductId.from(command.productId()))
            .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

    final var orderedIds = command.imageIds().stream()
            .map(ProductImageId::from)
            .toList();

    product.reorderImages(orderedIds, notification);

    if (notification.hasError()) {
        return Left(notification);
    }

    return Try(() -> transactionManager.execute(() -> {
        final var updated = productGateway.update(product);
        return ReorderProductImagesOutput.from(updated);
    })).toEither().mapLeft(Notification::create);
}
```

### 4. Persistência (Infrastructure)

Nenhum arquivo novo. O `ProductPostgresGateway.update()` já persiste o aggregate inteiro.

**Atenção ao mapeamento JPA de imagens** — verificar em `ProductJpaEntity`:
```java
@OneToMany(
        mappedBy = "product",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        orphanRemoval = true,       // ← obrigatório: garante que imagens removidas do aggregate sejam deletadas do banco
        fetch = FetchType.EAGER     // imagens são sempre carregadas junto ao produto
)
@OrderBy("sort_order ASC")
private List<ProductImageJpaEntity> images = new ArrayList<>();
```

> `orphanRemoval = true` é essencial. Sem ele, chamar `product.images.remove(img)` no aggregate não gerará `DELETE` no banco ao fazer `save()`.

E em `ProductImageJpaEntity.updateFrom()`:
```java
public void updateFrom(final ProductImage image) {
    this.url       = image.getUrl();
    this.altText   = image.getAltText();
    this.sortOrder = image.getSortOrder();
    this.primary   = image.isPrimary();
    // NUNCA sobrescrever id
}
```

### 5. Roteamento e Injeção (API)

**`AddProductImageRequest`**:
```java
public record AddProductImageRequest(
        @NotBlank(message = "URL da imagem é obrigatória")
        @Size(max = 2048, message = "URL não pode exceder 2048 caracteres")
        String url,

        @Size(max = 255, message = "Texto alternativo não pode exceder 255 caracteres")
        String altText,

        @Min(value = 1, message = "sortOrder deve ser maior que zero")
        int sortOrder,

        boolean primary
) {}
```

**`ReorderProductImagesRequest`**:
```java
public record ReorderProductImagesRequest(
        @NotEmpty(message = "A lista de IDs não pode ser vazia")
        List<@NotBlank String> imageIds
) {}
```

**Endpoints no `ProductController`**:
```java
@PostMapping("/{productId}/images")
@ResponseStatus(HttpStatus.CREATED)
@Operation(summary = "Adicionar imagem", description = "Adiciona uma nova imagem ao produto. Máximo de 10 imagens por produto.")
@ApiResponses({
        @ApiResponse(responseCode = "201", description = "Imagem adicionada"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "422", description = "Limite de imagens atingido ou URL duplicada")
})
public AddProductImageResponse addImage(
        @PathVariable final String productId,
        @Valid @RequestBody final AddProductImageRequest request) {
    return AddProductImageResponse.from(
            addProductImageUseCase
                    .execute(new AddProductImageCommand(
                            productId, request.url(), request.altText(),
                            request.sortOrder(), request.primary()))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}

@DeleteMapping("/{productId}/images/{imageId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
@Operation(summary = "Remover imagem", description = "Remove uma imagem do produto.")
@ApiResponses({
        @ApiResponse(responseCode = "204", description = "Imagem removida"),
        @ApiResponse(responseCode = "404", description = "Produto ou imagem não encontrada"),
        @ApiResponse(responseCode = "422", description = "Imagem não pertence ao produto")
})
public void removeImage(
        @PathVariable final String productId,
        @PathVariable final String imageId) {
    removeProductImageUseCase
            .execute(new RemoveProductImageCommand(productId, imageId))
            .getOrElseThrow(n -> DomainException.with(n.getErrors()));
}

@PatchMapping("/{productId}/images/{imageId}/primary")
@ResponseStatus(HttpStatus.OK)
@Operation(summary = "Definir imagem primária", description = "Define esta imagem como primária e desmarca todas as outras.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Imagem primária atualizada"),
        @ApiResponse(responseCode = "404", description = "Produto ou imagem não encontrada"),
        @ApiResponse(responseCode = "422", description = "Imagem não pertence ao produto")
})
public SetPrimaryProductImageResponse setPrimary(
        @PathVariable final String productId,
        @PathVariable final String imageId) {
    return SetPrimaryProductImageResponse.from(
            setPrimaryProductImageUseCase
                    .execute(new SetPrimaryProductImageCommand(productId, imageId))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}

@PutMapping("/{productId}/images/reorder")
@ResponseStatus(HttpStatus.OK)
@Operation(summary = "Reordenar imagens", description = "Atualiza a ordem das imagens. A lista deve conter todos os IDs das imagens do produto.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ordem atualizada"),
        @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
        @ApiResponse(responseCode = "422", description = "Lista incompleta ou ID inválido")
})
public ReorderProductImagesResponse reorder(
        @PathVariable final String productId,
        @Valid @RequestBody final ReorderProductImagesRequest request) {
    return ReorderProductImagesResponse.from(
            reorderProductImagesUseCase
                    .execute(new ReorderProductImagesCommand(productId, request.imageIds()))
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar 4 beans:
```java
@Bean
public AddProductImageUseCase addProductImageUseCase(
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new AddProductImageUseCase(productGateway, eventPublisher, transactionManager);
}

@Bean
public RemoveProductImageUseCase removeProductImageUseCase(
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new RemoveProductImageUseCase(productGateway, eventPublisher, transactionManager);
}

@Bean
public SetPrimaryProductImageUseCase setPrimaryProductImageUseCase(
        final ProductGateway productGateway,
        final TransactionManager transactionManager
) {
    return new SetPrimaryProductImageUseCase(productGateway, transactionManager);
}

@Bean
public ReorderProductImagesUseCase reorderProductImagesUseCase(
        final ProductGateway productGateway,
        final TransactionManager transactionManager
) {
    return new ReorderProductImagesUseCase(productGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `ProductError.PRODUCT_NOT_FOUND` | `productId` não existe | `404 Not Found` |
| `ProductError.PRODUCT_IMAGE_NOT_FOUND` | `imageId` não pertence ao produto | `422 Unprocessable Entity` |
| `ProductError.PRODUCT_IMAGE_LIMIT_EXCEEDED` | Produto já tem 10 imagens | `422 Unprocessable Entity` |
| `ProductError.PRODUCT_IMAGE_URL_ALREADY_EXISTS` | URL já cadastrada neste produto | `422 Unprocessable Entity` |
| `ProductError.PRODUCT_IMAGE_REORDER_INCOMPLETE` | Lista de reordenação não contém todos os IDs | `422 Unprocessable Entity` |
| `ProductError.CANNOT_MODIFY_DELETED_PRODUCT` | Produto com `deleted_at` preenchido | `422 Unprocessable Entity` |
| `ObjectOptimisticLockingFailureException` | Versão desatualizada (concorrência) | `409 Conflict` |

---

## 🌐 Contrato da API REST

### `POST /api/v1/catalog/products/{productId}/images` — Adicionar imagem

**Request**:
```json
{
  "url": "https://cdn.btree.com/products/tenis-x2-side.jpg",
  "altText": "Tênis Running Pro X2 - Vista lateral",
  "sortOrder": 2,
  "primary": false
}
```

**Response (201 Created)**:
```json
{
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "images": [
    {
      "id": "01965f3a-0000-7000-0000-000000000020",
      "url": "https://cdn.btree.com/products/tenis-x2-front.jpg",
      "altText": "Tênis Running Pro X2 - Vista frontal",
      "sortOrder": 1,
      "primary": true
    },
    {
      "id": "01965f3a-0000-7000-0000-000000000021",
      "url": "https://cdn.btree.com/products/tenis-x2-side.jpg",
      "altText": "Tênis Running Pro X2 - Vista lateral",
      "sortOrder": 2,
      "primary": false
    }
  ]
}
```

### `DELETE /api/v1/catalog/products/{productId}/images/{imageId}` — Remover imagem

**Response (204 No Content)** — sem corpo.

### `PATCH /api/v1/catalog/products/{productId}/images/{imageId}/primary` — Definir primária

**Response (200 OK)**:
```json
{
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "images": [
    {
      "id": "01965f3a-0000-7000-0000-000000000020",
      "url": "https://cdn.btree.com/products/tenis-x2-front.jpg",
      "altText": "Vista frontal",
      "sortOrder": 1,
      "primary": false
    },
    {
      "id": "01965f3a-0000-7000-0000-000000000021",
      "url": "https://cdn.btree.com/products/tenis-x2-side.jpg",
      "altText": "Vista lateral",
      "sortOrder": 2,
      "primary": true
    }
  ]
}
```

### `PUT /api/v1/catalog/products/{productId}/images/reorder` — Reordenar

**Request**:
```json
{
  "imageIds": [
    "01965f3a-0000-7000-0000-000000000021",
    "01965f3a-0000-7000-0000-000000000020"
  ]
}
```

**Response (200 OK)**:
```json
{
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "images": [
    {
      "id": "01965f3a-0000-7000-0000-000000000021",
      "url": "https://cdn.btree.com/products/tenis-x2-side.jpg",
      "altText": "Vista lateral",
      "sortOrder": 1,
      "primary": true
    },
    {
      "id": "01965f3a-0000-7000-0000-000000000020",
      "url": "https://cdn.btree.com/products/tenis-x2-front.jpg",
      "altText": "Vista frontal",
      "sortOrder": 2,
      "primary": false
    }
  ]
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["O produto já atingiu o limite máximo de 10 imagens."],
  "timestamp": "2026-04-10T22:00:00Z",
  "path": "/api/v1/catalog/products/01965f3a-0000-7000-0000-000000000010/images"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. **Adicionar em `ProductError`** — `PRODUCT_IMAGE_NOT_FOUND`, `PRODUCT_IMAGE_URL_ALREADY_EXISTS`, `PRODUCT_IMAGE_LIMIT_EXCEEDED`, `PRODUCT_IMAGE_REORDER_INCOMPLETE`, `CANNOT_MODIFY_DELETED_PRODUCT`.
2. **Criar `ProductImageAddedEvent`** e **`ProductImageRemovedEvent`** — Domain Events com `productId` e `imageId`.
3. **Refatorar `Product.java`** — adicionar `addImage()`, `removeImage()`, `setPrimaryImage()`, `reorderImages()` com `Notification`.
4. **Verificar `ProductJpaEntity`** — confirmar `orphanRemoval = true` na coleção de imagens; ajustar `ProductImageJpaEntity.updateFrom()` se necessário.
5. **`AddProductImageCommand/Output/UseCase`** — adiciona imagem; se `primary=true`, chama `setPrimaryImage()` em seguida.
6. **`RemoveProductImageCommand` + `RemoveProductImageUseCase`** — `UnitUseCase`, retorna 204.
7. **`SetPrimaryProductImageCommand/Output/UseCase`** — delega para `product.setPrimaryImage()`.
8. **`ReorderProductImagesCommand/Output/UseCase`** — converte IDs e delega para `product.reorderImages()`.
9. **`@Bean`s em `UseCaseConfig`** — wiring dos 4 use cases.
10. **`AddProductImageRequest`** e **`AddProductImageResponse`** — com Bean Validation.
11. **`ReorderProductImagesRequest`** — lista de IDs com `@NotEmpty`.
12. **Responses restantes** — `SetPrimaryProductImageResponse`, `ReorderProductImagesResponse`.
13. **Endpoints no `ProductController`** — POST images, DELETE image, PATCH primary, PUT reorder.
14. **Testes unitários (domain)** — `Product.addImage()`: limite, URL duplicada, deletado; `Product.removeImage()`: ID inválido; `Product.setPrimaryImage()`: troca de primária; `Product.reorderImages()`: lista incompleta.
15. **Testes unitários (application)** — 4 use cases com Mockito.
16. **Testes de integração** (`ManageProductImagesIT.java`) — Testcontainers; inserir produto, adicionar 2 imagens, definir primária, reordenar, remover; verificar estado final na tabela `catalog.product_images`.
