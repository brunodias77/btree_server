# Task: UC-87 — AddItemToCart

## 📋 Resumo

Adiciona um produto ao carrinho ativo do usuário (autenticado ou guest). Se o produto já estiver no carrinho, a quantidade é **incrementada**. Se o usuário não tiver carrinho ativo, um novo carrinho é criado automaticamente antes de adicionar o item. Registra a ação no `cart.cart_activity_log` e publica os eventos de domínio via Outbox.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/cart/items` deve:

1. Identificar o carrinho ativo do usuário (`userId`) ou da sessão (`sessionId`).
2. Se não existir carrinho ativo, criar um novo (`Cart.create()`).
3. Verificar que o produto existe e está `ACTIVE` (consulta ao `ProductGateway`).
4. Buscar o preço atual do produto para usar como `unitPrice` do item.
5. Chamar `cart.addItem(productId, quantity, unitPrice)` no aggregate (merge automático se produto já existe).
6. Persistir o carrinho (create ou update) e gravar o `CartActivityLog`.
7. Publicar `CartCreatedEvent` (se novo) e `ItemAddedToCartEvent` via Outbox.
8. Retornar `200 OK` com o estado atualizado do carrinho.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/cart/items`
* **Tabelas do Banco:**
  - `cart.carts` — criação ou atualização do carrinho
  - `cart.cart_items` — inserção ou atualização do item (UNIQUE `cart_id, product_id`)
  - `catalog.products` — leitura de status e preço atual
  - `cart.cart_activity_log` — registro imutável da ação

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos esses arquivos **já existem**. Apenas verificações.

1. **[VERIFICAR]** `domain/.../cart/entity/Cart.java` — confirmar que `addItem(UUID, int, BigDecimal)` faz merge se produto já existe, incrementando a quantidade.
2. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `save()`, `update()`, `findActiveByUserId()`, `findActiveBySessionId()`.
3. **[VERIFICAR]** `domain/.../cart/gateway/CartActivityLogGateway.java` — confirmar `save()`.
4. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar `findById()`.
5. **[VERIFICAR]** `domain/.../cart/error/CartError.java` — confirmar `CART_NOT_ACTIVE`.
6. **[VERIFICAR]** `domain/.../cart/error/CartItemError.java` — confirmar `QUANTITY_NOT_POSITIVE`, `UNIT_PRICE_NULL`, etc.
7. **[VERIFICAR]** `domain/.../cart/events/CartCreatedEvent.java` e `ItemAddedToCartEvent.java` — já existem.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/AddItemToCartCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/AddItemToCartOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/AddItemToCartUseCase.java`

### `infrastructure`

> Nenhum arquivo de domínio novo. Verificar se os gateways do carrinho já existem.

1. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/entity/CartJpaEntity.java`
2. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/entity/CartItemJpaEntity.java`
3. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/entity/CartActivityLogJpaEntity.java`
4. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/persistence/CartJpaRepository.java`
5. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/persistence/CartItemJpaRepository.java`
6. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/persistence/CartActivityLogJpaRepository.java`
7. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/persistence/CartPostgresGateway.java`
8. **[CRIAR SE AUSENTE]** `infrastructure/.../cart/persistence/CartActivityLogPostgresGateway.java`

### `api`

1. **[CRIAR]** `api/.../cart/AddItemToCartRequest.java`
2. **[CRIAR]** `api/.../cart/CartResponse.java`
3. **[CRIAR]** `api/.../cart/CartController.java`
4. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean addItemToCartUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`AddItemToCartCommand`**:
```java
package com.btree.application.usecase.cart;

import java.util.UUID;

/**
 * Entrada para UC-87 — AddItemToCart.
 *
 * @param userId     UUID do usuário autenticado (null se guest)
 * @param sessionId  ID de sessão do guest (null se autenticado)
 * @param productId  UUID do produto a adicionar
 * @param quantity   Quantidade a adicionar (≥ 1)
 */
public record AddItemToCartCommand(
        String userId,
        String sessionId,
        String productId,
        int quantity
) {}
```

**`AddItemToCartOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-87 — AddItemToCart.
 */
public record AddItemToCartOutput(
        String cartId,
        String status,
        String couponCode,
        BigDecimal subtotal,
        int totalItems,
        List<CartItemOutput> items,
        Instant updatedAt
) {
    public record CartItemOutput(
            String cartItemId,
            String productId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public static CartItemOutput from(final CartItem item) {
            return new CartItemOutput(
                    item.getId().getValue().toString(),
                    item.getProductId().toString(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.subtotal()
            );
        }
    }

    public static AddItemToCartOutput from(final Cart cart) {
        return new AddItemToCartOutput(
                cart.getId().getValue().toString(),
                cart.getStatus().name(),
                cart.getCouponCode(),
                cart.subtotal(),
                cart.totalItems(),
                cart.getItems().stream().map(CartItemOutput::from).toList(),
                cart.getUpdatedAt()
        );
    }
}
```

### 2. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.error.CartItemError;
import com.btree.domain.cart.gateway.CartActivityLogGateway;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * UC-87 — AddItemToCart [CMD P0].
 *
 * <p>Adiciona um produto ao carrinho ativo do usuário ou sessão.
 * Se não houver carrinho ativo, cria um novo automaticamente.
 * Produtos já existentes no carrinho têm a quantidade incrementada (merge).
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Localizar carrinho ativo por userId ou sessionId.</li>
 *   <li>Se não existir, criar novo carrinho via {@code Cart.create()}.</li>
 *   <li>Buscar produto — {@code NotFoundException} se ausente ou deletado.</li>
 *   <li>Validar: produto deve ter status {@code ACTIVE} e quantity disponível ≥ 1.</li>
 *   <li>Chamar {@code cart.addItem(productId, quantity, price)} — o aggregate
 *       faz o merge automaticamente se o produto já existir.</li>
 *   <li>Dentro da transação: persistir carrinho, gravar {@code CartActivityLog},
 *       publicar eventos.</li>
 * </ol>
 */
public class AddItemToCartUseCase implements UseCase<AddItemToCartCommand, AddItemToCartOutput> {

    private static final int CART_TTL_DAYS_GUEST = 1;
    private static final int CART_TTL_DAYS_USER  = 30;

    private final CartGateway            cartGateway;
    private final CartActivityLogGateway activityLogGateway;
    private final ProductGateway         productGateway;
    private final DomainEventPublisher   eventPublisher;
    private final TransactionManager     transactionManager;

    public AddItemToCartUseCase(
            final CartGateway cartGateway,
            final CartActivityLogGateway activityLogGateway,
            final ProductGateway productGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.cartGateway        = cartGateway;
        this.activityLogGateway = activityLogGateway;
        this.productGateway     = productGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, AddItemToCartOutput> execute(final AddItemToCartCommand command) {

        // 1. Validações de entrada antes de qualquer I/O
        final var notification = Notification.create();

        if (command.quantity() <= 0) {
            notification.append(CartItemError.QUANTITY_NOT_POSITIVE);
        }
        if (command.userId() == null && command.sessionId() == null) {
            notification.append(new com.btree.shared.validation.Error(
                    "'userId' ou 'sessionId' é obrigatório"));
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 2. Parsear UUIDs
        final UUID userId;
        final UUID productId;
        try {
            userId    = command.userId() != null ? UUID.fromString(command.userId()) : null;
            productId = UUID.fromString(command.productId());
        } catch (final IllegalArgumentException e) {
            notification.append(new com.btree.shared.validation.Error("UUID inválido fornecido"));
            return Left(notification);
        }

        // 3. Buscar produto — NotFoundException propaga como 404
        final var product = productGateway.findById(ProductId.from(productId))
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND));

        if (product.isDeleted()) {
            notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
        }
        if (!product.getStatus().name().equals("ACTIVE")) {
            notification.append(new com.btree.shared.validation.Error(
                    "Produto não está disponível para compra"));
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 4. Localizar ou criar carrinho ativo
        final var existingCart = userId != null
                ? cartGateway.findActiveByUserId(userId)
                : cartGateway.findActiveBySessionId(command.sessionId());

        final boolean isNewCart = existingCart.isEmpty();
        final var ttlDays = userId != null ? CART_TTL_DAYS_USER : CART_TTL_DAYS_GUEST;
        final var expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);

        final Cart cart = existingCart.orElseGet(() ->
                Cart.create(userId, command.sessionId(), expiresAt)
        );

        // 5. Adicionar item ao aggregate (merge automático se produto já existe)
        cart.addItem(productId, command.quantity(), product.getPrice());

        // 6. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 6a. Salvar ou atualizar carrinho
            final Cart savedCart = isNewCart
                    ? cartGateway.save(cart)
                    : cartGateway.update(cart);

            // 6b. Gravar audit log imutável
            final var log = CartActivityLog.create(
                    savedCart.getId(),
                    "ADD_ITEM",
                    productId,
                    command.quantity(),
                    null
            );
            activityLogGateway.save(log);

            // 6c. Publicar todos os domain events acumulados no aggregate
            //     (CartCreatedEvent se novo + ItemAddedToCartEvent)
            eventPublisher.publishAll(savedCart.getDomainEvents());

            return AddItemToCartOutput.from(savedCart);
        })).toEither().mapLeft(Notification::create);
    }
}
```

> **Nota sobre `Cart.create()`**: verificar a assinatura exata do factory method no aggregate. O `Cart.create()` deve registrar `CartCreatedEvent` internamente. Caso a assinatura atual não aceite `expiresAt`, ajustar a chamada conforme o aggregate existente.

### 3. Infraestrutura — JPA Entities

Como o módulo `cart` não tem gateways JPA implementados, estes precisam ser criados. Seguir o mesmo padrão adotado no `user` e `catalog`.

**`CartJpaEntity`**:
```java
@Entity
@Table(name = "carts", schema = "cart")
public class CartJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_id")
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CartStatus status;

    @Column(name = "coupon_code")
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method")
    private ShippingMethod shippingMethod;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(
            mappedBy = "cart",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH},
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<CartItemJpaEntity> items = new ArrayList<>();

    public static CartJpaEntity from(final Cart cart) { ... }

    public Cart toAggregate() { ... }

    public void updateFrom(final Cart cart) {
        // Atualiza campos mutáveis — NUNCA sobrescreve id ou version
        this.status = cart.getStatus();
        this.couponCode = cart.getCouponCode();
        this.shippingMethod = cart.getShippingMethod();
        this.notes = cart.getNotes();
        this.updatedAt = cart.getUpdatedAt();
        this.expiresAt = cart.getExpiresAt();
        // Sincroniza items via orphanRemoval
    }
}
```

**`CartItemJpaEntity`**:
```java
@Entity
@Table(
    name = "cart_items",
    schema = "cart",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"})
)
public class CartItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartJpaEntity cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CartItemJpaEntity from(final CartItem item, final CartJpaEntity cart) { ... }

    public CartItem toAggregate(final CartId cartId) { ... }
}
```

**`CartActivityLogJpaEntity`**:
```java
@Entity
@Table(name = "cart_activity_log", schema = "cart")
public class CartActivityLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CartActivityLogJpaEntity from(final CartActivityLog log) { ... }

    public CartActivityLog toAggregate() { ... }
}
```

**`CartPostgresGateway`**:
```java
@Component
@Transactional
public class CartPostgresGateway implements CartGateway {

    private final CartJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findActiveByUserId(final UUID userId) {
        return repository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .map(CartJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findActiveBySessionId(final String sessionId) {
        return repository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
                .map(CartJpaEntity::toAggregate);
    }

    @Override
    public Cart save(final Cart cart) {
        return repository.save(CartJpaEntity.from(cart)).toAggregate();
    }

    @Override
    public Cart update(final Cart cart) {
        final var entity = repository.findById(cart.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND));
        entity.updateFrom(cart);
        return repository.save(entity).toAggregate();
    }
}
```

**`CartJpaRepository`**:
```java
public interface CartJpaRepository extends JpaRepository<CartJpaEntity, UUID> {

    Optional<CartJpaEntity> findByUserIdAndStatus(UUID userId, CartStatus status);

    Optional<CartJpaEntity> findBySessionIdAndStatus(String sessionId, CartStatus status);

    List<CartJpaEntity> findByStatus(CartStatus status);

    @Query("SELECT c FROM CartJpaEntity c WHERE c.status = 'ACTIVE' AND c.expiresAt < :now")
    List<CartJpaEntity> findExpiredActiveCarts(@Param("now") Instant now);
}
```

### 4. Roteamento e Injeção (API)

**`AddItemToCartRequest`**:
```java
package com.btree.api.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body para POST /api/v1/cart/items.
 */
public record AddItemToCartRequest(

        @NotBlank(message = "'productId' é obrigatório")
        String productId,

        @NotNull(message = "'quantity' é obrigatório")
        @Min(value = 1, message = "'quantity' deve ser maior que zero")
        Integer quantity,

        String sessionId    // opcional — usado apenas em carrinhos guest
) {}
```

**`CartResponse`**:
```java
package com.btree.api.cart;

import com.btree.application.usecase.cart.AddItemToCartOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        String cartId,
        String status,
        String couponCode,
        BigDecimal subtotal,
        int totalItems,
        List<CartItemResponse> items,
        Instant updatedAt
) {
    public record CartItemResponse(
            String cartItemId,
            String productId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}

    public static CartResponse from(final AddItemToCartOutput output) {
        return new CartResponse(
                output.cartId(),
                output.status(),
                output.couponCode(),
                output.subtotal(),
                output.totalItems(),
                output.items().stream()
                        .map(i -> new CartItemResponse(
                                i.cartItemId(), i.productId(),
                                i.quantity(), i.unitPrice(), i.subtotal()))
                        .toList(),
                output.updatedAt()
        );
    }
}
```

**`CartController`**:
```java
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Gerenciamento do carrinho de compras")
public class CartController {

    private final AddItemToCartUseCase addItemToCartUseCase;

    public CartController(final AddItemToCartUseCase addItemToCartUseCase) {
        this.addItemToCartUseCase = addItemToCartUseCase;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Adicionar item ao carrinho",
            description = "Adiciona um produto ao carrinho ativo. Cria o carrinho automaticamente " +
                          "se não existir. Se o produto já estiver no carrinho, incrementa a quantidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item adicionado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "422", description = "Produto inativo, deletado ou quantidade inválida")
    })
    public CartResponse addItem(
            @RequestHeader(value = "X-User-Id", required = false) final String userId,
            @Valid @RequestBody final AddItemToCartRequest request
    ) {
        final var command = new AddItemToCartCommand(
                userId,
                request.sessionId(),
                request.productId(),
                request.quantity()
        );
        return CartResponse.from(
                addItemToCartUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

> O `userId` é extraído do header `X-User-Id`, que deve ser populado pelo `JwtAuthenticationFilter` após validar o token. Em carts guest (sem token), o header estará ausente e o `sessionId` do body é usado.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public AddItemToCartUseCase addItemToCartUseCase(
        final CartGateway cartGateway,
        final CartActivityLogGateway cartActivityLogGateway,
        final ProductGateway productGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new AddItemToCartUseCase(
            cartGateway,
            cartActivityLogGateway,
            productGateway,
            eventPublisher,
            transactionManager
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| Produto não encontrado | `NotFoundException` (lançada diretamente) | `productGateway.findById()` vazio | `404 Not Found` |
| Produto deletado | `ProductError.CANNOT_MODIFY_DELETED_PRODUCT` | `product.isDeleted() == true` | `422 Unprocessable Entity` |
| Produto inativo | erro inline | `product.getStatus() != ACTIVE` | `422 Unprocessable Entity` |
| Quantity ≤ 0 | `CartItemError.QUANTITY_NOT_POSITIVE` | `command.quantity() <= 0` | `422 Unprocessable Entity` |
| Sem userId e sessionId | erro inline | ambos `null` | `422 Unprocessable Entity` |
| UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |
| Carrinho não ativo | `CartError.CART_NOT_ACTIVE` | aggregate `assertActive()` lança `DomainException` | `422 Unprocessable Entity` |
| Conflito de version | `ObjectOptimisticLockingFailureException` | outra transação modificou o carrinho | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/cart/items`

```json
{
  "productId": "01965f3a-0000-7000-0000-000000000010",
  "quantity": 2,
  "sessionId": "sess_abc123"
}
```

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `productId` | `string (UUID)` | Sim | Produto deve existir e estar `ACTIVE` |
| `quantity` | `int` | Sim | ≥ 1 |
| `sessionId` | `string` | Condicional | Obrigatório se não houver header `X-User-Id` |

**Header:** `X-User-Id: {userId}` — presente para usuários autenticados, ausente para guests.

### Response (Sucesso — 200 OK)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 199.90,
  "totalItems": 2,
  "items": [
    {
      "cartItemId": "01965f3a-0000-7000-0000-000000000060",
      "productId": "01965f3a-0000-7000-0000-000000000010",
      "quantity": 2,
      "unitPrice": 99.95,
      "subtotal": 199.90
    }
  ],
  "updatedAt": "2026-04-11T15:30:00Z"
}
```

### Response (Erro — 422)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Produto não está disponível para compra"],
  "timestamp": "2026-04-11T15:30:00Z",
  "path": "/api/v1/cart/items"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Cart.create(userId, sessionId, expiresAt)`** — confirmar assinatura e que registra `CartCreatedEvent`.
2. **`CartJpaEntity`** + **`CartItemJpaEntity`** + **`CartActivityLogJpaEntity`** — criar as três JPA entities com `from()`, `toAggregate()` e `updateFrom()`.
3. **`CartJpaRepository`** + **`CartItemJpaRepository`** + **`CartActivityLogJpaRepository`** — interfaces Spring Data.
4. **`CartPostgresGateway`** — implementar `CartGateway` com `@Transactional`.
5. **`CartActivityLogPostgresGateway`** — implementar `CartActivityLogGateway`.
6. **`AddItemToCartCommand`** — record com 4 campos.
7. **`AddItemToCartOutput`** — record com nested `CartItemOutput` e factory `from(Cart)`.
8. **`AddItemToCartUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
9. **`@Bean addItemToCartUseCase`** em `UseCaseConfig`.
10. **`AddItemToCartRequest`** — record com `@NotBlank` e `@Min(1)`.
11. **`CartResponse`** — record com factory `from(AddItemToCartOutput)`.
12. **`CartController`** — criar o controller com endpoint único.
13. **Testes unitários** — `AddItemToCartUseCaseTest` com Mockito (sem Spring):
    - usuário sem carrinho → carrinho criado, item adicionado, eventos publicados
    - usuário com carrinho ativo → item adicionado ao existente, `CartCreatedEvent` **não** publicado
    - produto já no carrinho → quantidade incrementada (merge)
    - produto não encontrado → `NotFoundException` propagada (404)
    - produto inativo → `Left(notification)` com erro de produto não disponível
    - produto deletado → `Left(notification)` com `CANNOT_MODIFY_DELETED_PRODUCT`
    - `quantity <= 0` → `Left(notification)` com `QUANTITY_NOT_POSITIVE`
    - sem `userId` e sem `sessionId` → `Left(notification)` com erro de identificação
    - falha em `activityLogGateway.save()` → rollback de `cartGateway.save()` / `update()`
14. **Testes de integração** (`AddItemToCartIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `cart.carts` criado com `status = ACTIVE` e `version = 1`.
    - `cart.cart_items` com `UNIQUE(cart_id, product_id)` — segunda adição do mesmo produto atualiza quantity.
    - `cart.cart_activity_log` registra linha com `action = ADD_ITEM`.
    - Otimistic locking: duas threads adicionando ao mesmo carrinho simultaneamente → apenas uma vence.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-88 RemoveItemFromCart** | Remove um item adicionado por este UC |
| **UC-89 UpdateCartItemQuantity** | Atualiza a quantidade de um item existente |
| **UC-90 GetCart** | Retorna o estado atual do carrinho (leitura) |
| **UC-71 ReserveStock** | Chamado durante o checkout para reservar o estoque dos itens do carrinho |
| **UC-100 PlaceOrder** | Converte o carrinho em pedido (`cart.markAsConverted()`) |
