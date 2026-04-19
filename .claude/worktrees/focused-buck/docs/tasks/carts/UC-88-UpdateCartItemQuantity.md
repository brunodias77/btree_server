# Task: UC-88 — UpdateCartItemQuantity

## 📋 Resumo

Atualiza a quantidade de um produto já existente no carrinho ativo do usuário. Comportamento especial: se a nova quantidade for **zero**, o item é **removido automaticamente** do carrinho (delegado ao behavior `cart.updateItemQuantity()` do aggregate, que internamente chama `cart.removeItem()` quando `quantity == 0`).

## 🎯 Objetivo

Ao final da implementação, o endpoint `PATCH /api/v1/cart/items/{productId}` deve:

1. Identificar o carrinho ativo do usuário (`userId` ou `sessionId`).
2. Validar que o item referenciado (`productId`) está no carrinho.
3. Chamar `cart.updateItemQuantity(productId, quantity)` no aggregate.
   - Se `quantity == 0`, o item é removido automaticamente.
   - Se `quantity > 0`, a quantidade é atualizada.
4. Persistir o carrinho via `cartGateway.update()`.
5. Gravar `CartActivityLog` com a ação adequada (`UPDATE_ITEM_QUANTITY` ou `REMOVE_ITEM`).
6. Publicar eventos de domínio via Outbox.
7. Retornar `200 OK` com o estado atualizado do carrinho.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `PATCH /api/v1/cart/items/{productId}`
* **Tabelas do Banco:**
  - `cart.cart_items` — atualização de `quantity` ou remoção da linha (via `orphanRemoval`)
  - `cart.carts` — atualização de `updated_at` e `version`
  - `cart.cart_activity_log` — registro da ação

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos esses arquivos **já existem**. Apenas verificações.

1. **[VERIFICAR]** `domain/.../cart/entity/Cart.java` — confirmar que `updateItemQuantity(UUID productId, int quantity)` remove o item quando `quantity == 0` e lança `DomainException` se o produto não estiver no carrinho.
2. **[VERIFICAR]** `domain/.../cart/error/CartItemError.java` — confirmar `ITEM_NOT_FOUND`.
3. **[VERIFICAR]** `domain/.../cart/error/CartError.java` — confirmar `CART_NOT_FOUND`, `CART_NOT_ACTIVE`.
4. **[VERIFICAR]** `domain/.../cart/events/ItemRemovedFromCartEvent.java` — já existe (disparado quando `quantity == 0`).
5. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()`, `findActiveBySessionId()`, `update()`.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/UpdateCartItemQuantityCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/UpdateCartItemQuantityOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/UpdateCartItemQuantityUseCase.java`

### `infrastructure`

> Sem novos arquivos — todas as JPA entities e gateways do cart são criados no UC-87. Apenas verificar se estão completos.

1. **[VERIFICAR]** `infrastructure/.../cart/persistence/CartPostgresGateway.java` — confirmar `update()` e `findActiveByUserId()` / `findActiveBySessionId()`.
2. **[VERIFICAR]** `infrastructure/.../cart/persistence/CartActivityLogPostgresGateway.java` — confirmar `save()`.

### `api`

1. **[CRIAR]** `api/.../cart/UpdateCartItemQuantityRequest.java`
2. **[ALTERAR]** `api/.../cart/CartController.java` — adicionar endpoint `PATCH /items/{productId}`.
3. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean updateCartItemQuantityUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`UpdateCartItemQuantityCommand`**:
```java
package com.btree.application.usecase.cart;

/**
 * Entrada para UC-88 — UpdateCartItemQuantity.
 *
 * @param userId     UUID do usuário autenticado (null se guest)
 * @param sessionId  ID de sessão do guest (null se autenticado)
 * @param productId  UUID do produto cujo item será atualizado
 * @param quantity   Nova quantidade (0 = remove o item)
 */
public record UpdateCartItemQuantityCommand(
        String userId,
        String sessionId,
        String productId,
        int quantity
) {}
```

**`UpdateCartItemQuantityOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-88 — UpdateCartItemQuantity.
 * Retorna o estado completo do carrinho após a operação.
 */
public record UpdateCartItemQuantityOutput(
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

    public static UpdateCartItemQuantityOutput from(final Cart cart) {
        return new UpdateCartItemQuantityOutput(
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

import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.error.CartItemError;
import com.btree.domain.cart.gateway.CartActivityLogGateway;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * UC-88 — UpdateCartItemQuantity [CMD P0].
 *
 * <p>Atualiza a quantidade de um item no carrinho ativo.
 * Se {@code quantity == 0}, o item é removido automaticamente pelo aggregate.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Validar entrada: {@code quantity} ≥ 0 e UUIDs válidos.</li>
 *   <li>Localizar carrinho ativo — {@code NotFoundException} se ausente.</li>
 *   <li>Chamar {@code cart.updateItemQuantity(productId, quantity)} — lança
 *       {@code DomainException} se o produto não estiver no carrinho.</li>
 *   <li>Dentro da transação: persistir carrinho e gravar {@code CartActivityLog}.</li>
 * </ol>
 */
public class UpdateCartItemQuantityUseCase
        implements UseCase<UpdateCartItemQuantityCommand, UpdateCartItemQuantityOutput> {

    private final CartGateway            cartGateway;
    private final CartActivityLogGateway activityLogGateway;
    private final DomainEventPublisher   eventPublisher;
    private final TransactionManager     transactionManager;

    public UpdateCartItemQuantityUseCase(
            final CartGateway cartGateway,
            final CartActivityLogGateway activityLogGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.cartGateway        = cartGateway;
        this.activityLogGateway = activityLogGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateCartItemQuantityOutput> execute(
            final UpdateCartItemQuantityCommand command
    ) {
        // 1. Validações de entrada
        final var notification = Notification.create();

        if (command.quantity() < 0) {
            notification.append(new com.btree.shared.validation.Error(
                    "'quantity' não pode ser negativo"));
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

        // 3. Localizar carrinho ativo — NotFoundException propaga como 404
        final var cart = (userId != null
                ? cartGateway.findActiveByUserId(userId)
                : cartGateway.findActiveBySessionId(command.sessionId()))
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND));

        // 4. Verificar que o item existe no carrinho antes de entrar na transação
        final boolean itemExists = cart.getItems().stream()
                .anyMatch(i -> i.getProductId().equals(productId));
        if (!itemExists) {
            notification.append(CartItemError.ITEM_NOT_FOUND);
            return Left(notification);
        }

        // 5. Determinar a ação (para o log)
        final String action = command.quantity() == 0 ? "REMOVE_ITEM" : "UPDATE_ITEM_QUANTITY";

        // 6. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 6a. Mutação no aggregate:
            //     - quantity > 0 → atualiza a quantidade do item
            //     - quantity == 0 → remove o item (e registra ItemRemovedFromCartEvent)
            cart.updateItemQuantity(productId, command.quantity());
            final var updatedCart = cartGateway.update(cart);

            // 6b. Gravar audit log
            final var log = CartActivityLog.create(
                    updatedCart.getId(),
                    action,
                    productId,
                    command.quantity(),
                    null
            );
            activityLogGateway.save(log);

            // 6c. Publicar domain events acumulados no aggregate
            eventPublisher.publishAll(updatedCart.getDomainEvents());

            return UpdateCartItemQuantityOutput.from(updatedCart);
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 3. Roteamento e Injeção (API)

**`UpdateCartItemQuantityRequest`**:
```java
package com.btree.api.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body para PATCH /api/v1/cart/items/{productId}.
 */
public record UpdateCartItemQuantityRequest(

        @NotNull(message = "'quantity' é obrigatório")
        @Min(value = 0, message = "'quantity' não pode ser negativo")
        Integer quantity,

        String sessionId    // opcional — necessário apenas em carrinhos guest
) {}
```

**Endpoint no `CartController`**:
```java
@PatchMapping("/items/{productId}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Atualizar quantidade de item no carrinho",
        description = "Atualiza a quantidade de um produto no carrinho ativo. " +
                      "Se quantity = 0, o item é removido automaticamente.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quantidade atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "404", description = "Carrinho não encontrado ou item não está no carrinho"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Carrinho não está ativo")
})
public CartResponse updateItemQuantity(
        @RequestHeader(value = "X-User-Id", required = false) final String userId,
        @PathVariable final String productId,
        @Valid @RequestBody final UpdateCartItemQuantityRequest request
) {
    final var command = new UpdateCartItemQuantityCommand(
            userId,
            request.sessionId(),
            productId,
            request.quantity()
    );
    return CartResponse.from(
            updateCartItemQuantityUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> `CartResponse` reutilizado do UC-87. Deve ser consolidado em um único record compartilhado no `api/catalog/` — neste caso, já estará em `api/cart/CartResponse.java`.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public UpdateCartItemQuantityUseCase updateCartItemQuantityUseCase(
        final CartGateway cartGateway,
        final CartActivityLogGateway cartActivityLogGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new UpdateCartItemQuantityUseCase(
            cartGateway,
            cartActivityLogGateway,
            eventPublisher,
            transactionManager
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| Carrinho não encontrado | `NotFoundException` (lançada diretamente) | Sem carrinho ativo para o userId/sessionId | `404 Not Found` |
| Item não encontrado no carrinho | `CartItemError.ITEM_NOT_FOUND` | `productId` não está na lista de itens | `422 Unprocessable Entity` |
| Carrinho não ativo | `CartError.CART_NOT_ACTIVE` | aggregate `assertActive()` lança `DomainException` | `422 Unprocessable Entity` |
| Quantity negativa | erro inline | `command.quantity() < 0` | `422 Unprocessable Entity` |
| Sem userId e sessionId | erro inline | ambos `null` | `422 Unprocessable Entity` |
| UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | outra transação modificou o carrinho | `409 Conflict` |

> **`quantity = 0` é válido** — sinaliza remoção do item. O valor mínimo permitido no Bean Validation é `@Min(0)`, não `@Min(1)`.

---

## 🌐 Contrato da API REST

### Request — `PATCH /api/v1/cart/items/{productId}`

```json
{
  "quantity": 3,
  "sessionId": "sess_abc123"
}
```

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `productId` | `UUID` (path) | Sim | produto já deve estar no carrinho |
| `quantity` | `int` (body) | Sim | ≥ 0; `0` remove o item |
| `sessionId` | `string` (body) | Condicional | obrigatório se sem header `X-User-Id` |

**Header:** `X-User-Id: {userId}` — presente para usuários autenticados.

### Response (Sucesso — 200 OK, quantity > 0)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 299.85,
  "totalItems": 3,
  "items": [
    {
      "cartItemId": "01965f3a-0000-7000-0000-000000000060",
      "productId": "01965f3a-0000-7000-0000-000000000010",
      "quantity": 3,
      "unitPrice": 99.95,
      "subtotal": 299.85
    }
  ],
  "updatedAt": "2026-04-11T15:35:00Z"
}
```

### Response (Sucesso — 200 OK, quantity = 0, item removido)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 0.00,
  "totalItems": 0,
  "items": [],
  "updatedAt": "2026-04-11T15:35:00Z"
}
```

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Item não encontrado no carrinho"],
  "timestamp": "2026-04-11T15:35:00Z",
  "path": "/api/v1/cart/items/01965f3a-0000-7000-0000-000000000010"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Cart.updateItemQuantity(UUID, int)`** — confirmar que `quantity == 0` chama `removeItem()` internamente e que `quantity > 0` chama `item.updateQuantity()`. Confirmar que lança `DomainException(ITEM_NOT_FOUND)` se o produto não existir.
2. **`UpdateCartItemQuantityCommand`** — record com 4 campos.
3. **`UpdateCartItemQuantityOutput`** — reutilizar a estrutura de `AddItemToCartOutput` (mesmo nested `CartItemOutput`); ou extrair um `CartOutput` compartilhado se preferir evitar duplicação.
4. **`UpdateCartItemQuantityUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
5. **`@Bean updateCartItemQuantityUseCase`** em `UseCaseConfig`.
6. **`UpdateCartItemQuantityRequest`** — record com `@Min(0)` em `quantity`.
7. **Endpoint `PATCH /items/{productId}`** no `CartController`.
8. **Testes unitários** — `UpdateCartItemQuantityUseCaseTest` com Mockito (sem Spring):
    - `quantity > 0` e item existe → quantidade atualizada, log gravado, eventos publicados
    - `quantity = 0` e item existe → item removido, `ItemRemovedFromCartEvent` publicado
    - item não encontrado no carrinho (antes da transação) → `Left(notification)` com `ITEM_NOT_FOUND`
    - carrinho não encontrado → `NotFoundException` propagada (404)
    - carrinho não ativo → `DomainException(CART_NOT_ACTIVE)` propagada (422)
    - `quantity < 0` → `Left(notification)` com erro de quantidade negativa
    - sem `userId` e sem `sessionId` → `Left(notification)` com erro de identificação
    - falha em `activityLogGateway.save()` → rollback de `cartGateway.update()`
    - confirmar que `productGateway` **nunca é chamado** (sem acesso ao catálogo neste UC)
9. **Testes de integração** (`UpdateCartItemQuantityIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `quantity > 0` → `cart.cart_items.quantity` atualizado, `cart.carts.version` incrementado.
    - `quantity = 0` → linha removida de `cart.cart_items` (via `orphanRemoval`).
    - `cart.cart_activity_log` registra linha com `action = UPDATE_ITEM_QUANTITY` ou `REMOVE_ITEM`.
    - Optimistic locking: duas threads atualizando o mesmo carrinho → apenas uma vence.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-87 AddItemToCart** | Cria os itens que este UC atualiza; cria as JPA entities e gateways necessários |
| **UC-89 RemoveItemFromCart** | Alternativa explícita; este UC já remove quando `quantity = 0` |
| **UC-90 GetCart** | Leitura do carrinho — retorna o mesmo shape de dados deste UC |
