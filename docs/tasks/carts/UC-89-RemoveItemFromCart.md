# Task: UC-89 — RemoveItemFromCart

## 📋 Resumo

Remove explicitamente um produto do carrinho ativo via endpoint `DELETE`. Funcionalmente é equivalente a chamar `UpdateCartItemQuantity` com `quantity = 0`, mas fornece uma semântica HTTP mais precisa (`DELETE` vs `PATCH`) e uma intenção mais clara no log de atividade.

> **Nota sobre "Soft delete (removed_at)":** o schema `cart.cart_items` **não possui** coluna `removed_at`. A remoção é física — a linha é deletada via `orphanRemoval` do JPA quando `cart.removeItem()` é chamado. O "soft delete" na lista de UCs refere-se à intenção de rastreabilidade, que é atendida pelo `cart.cart_activity_log` com `action = "REMOVE_ITEM"`.

## 🎯 Objetivo

Ao final da implementação, o endpoint `DELETE /api/v1/cart/items/{productId}` deve:

1. Identificar o carrinho ativo do usuário (`userId` ou `sessionId`).
2. Verificar que o item (`productId`) existe no carrinho.
3. Chamar `cart.removeItem(productId)` no aggregate, que registra `ItemRemovedFromCartEvent`.
4. Persistir o carrinho via `cartGateway.update()`.
5. Gravar `CartActivityLog` com `action = "REMOVE_ITEM"`.
6. Publicar `ItemRemovedFromCartEvent` via Outbox.
7. Retornar `200 OK` com o estado atualizado do carrinho.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `DELETE /api/v1/cart/items/{productId}`
* **Tabelas do Banco:**
  - `cart.cart_items` — remoção da linha via `orphanRemoval`
  - `cart.carts` — atualização de `updated_at` e `version`
  - `cart.cart_activity_log` — registro da remoção

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos já existem. Apenas verificações.

1. **[VERIFICAR]** `domain/.../cart/entity/Cart.java` — confirmar que `removeItem(UUID productId)` lança `DomainException(ITEM_NOT_FOUND)` se o produto não estiver na lista e registra `ItemRemovedFromCartEvent`.
2. **[VERIFICAR]** `domain/.../cart/events/ItemRemovedFromCartEvent.java` — já existe.
3. **[VERIFICAR]** `domain/.../cart/error/CartItemError.java` — confirmar `ITEM_NOT_FOUND`.
4. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()`, `findActiveBySessionId()`, `update()`.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/RemoveItemFromCartCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/RemoveItemFromCartOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/RemoveItemFromCartUseCase.java`

### `infrastructure`

> Sem novos arquivos — tudo criado no UC-87.

1. **[VERIFICAR]** `infrastructure/.../cart/persistence/CartPostgresGateway.java`
2. **[VERIFICAR]** `infrastructure/.../cart/persistence/CartActivityLogPostgresGateway.java`

### `api`

1. **[ALTERAR]** `api/.../cart/CartController.java` — adicionar endpoint `DELETE /items/{productId}`.
2. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean removeItemFromCartUseCase`.

> Sem `Request` record — o endpoint não tem body (`DELETE`). O `productId` vem do path e o `sessionId` (guest) pode ser passado via query param.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`RemoveItemFromCartCommand`**:
```java
package com.btree.application.usecase.cart;

/**
 * Entrada para UC-89 — RemoveItemFromCart.
 *
 * @param userId    UUID do usuário autenticado (null se guest)
 * @param sessionId ID de sessão do guest (null se autenticado)
 * @param productId UUID do produto a remover do carrinho
 */
public record RemoveItemFromCartCommand(
        String userId,
        String sessionId,
        String productId
) {}
```

**`RemoveItemFromCartOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-89 — RemoveItemFromCart.
 * Retorna o estado completo do carrinho após a remoção.
 */
public record RemoveItemFromCartOutput(
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

    public static RemoveItemFromCartOutput from(final Cart cart) {
        return new RemoveItemFromCartOutput(
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

> **Dica de refatoração**: Os `CartItemOutput` de UC-87, UC-88 e UC-89 são estruturalmente idênticos. Considerar extrair um único `CartOutput` record compartilhado em `api/cart/` para evitar duplicação.

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
 * UC-89 — RemoveItemFromCart [CMD P0].
 *
 * <p>Remove explicitamente um produto do carrinho ativo.
 * Fornece semântica HTTP clara ({@code DELETE}) com rastreabilidade
 * via {@code CartActivityLog}.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Validar UUIDs e identificação (userId ou sessionId).</li>
 *   <li>Localizar carrinho ativo — {@code NotFoundException} se ausente.</li>
 *   <li>Verificar que o item existe antes de entrar na transação.</li>
 *   <li>Chamar {@code cart.removeItem(productId)} — registra {@code ItemRemovedFromCartEvent}.</li>
 *   <li>Persistir carrinho, gravar log, publicar eventos.</li>
 * </ol>
 */
public class RemoveItemFromCartUseCase
        implements UseCase<RemoveItemFromCartCommand, RemoveItemFromCartOutput> {

    private final CartGateway            cartGateway;
    private final CartActivityLogGateway activityLogGateway;
    private final DomainEventPublisher   eventPublisher;
    private final TransactionManager     transactionManager;

    public RemoveItemFromCartUseCase(
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
    public Either<Notification, RemoveItemFromCartOutput> execute(
            final RemoveItemFromCartCommand command
    ) {
        // 1. Validar identificação
        final var notification = Notification.create();

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

        // 4. Verificar que o item está no carrinho antes de entrar na transação
        final boolean itemExists = cart.getItems().stream()
                .anyMatch(i -> i.getProductId().equals(productId));
        if (!itemExists) {
            notification.append(CartItemError.ITEM_NOT_FOUND);
            return Left(notification);
        }

        // 5. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 5a. Remover item do aggregate (registra ItemRemovedFromCartEvent)
            cart.removeItem(productId);
            final var updatedCart = cartGateway.update(cart);

            // 5b. Gravar audit log
            final var log = CartActivityLog.create(
                    updatedCart.getId(),
                    "REMOVE_ITEM",
                    productId,
                    null,
                    null
            );
            activityLogGateway.save(log);

            // 5c. Publicar domain events
            eventPublisher.publishAll(updatedCart.getDomainEvents());

            return RemoveItemFromCartOutput.from(updatedCart);
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 3. Roteamento e Injeção (API)

**Endpoint no `CartController`**:
```java
@DeleteMapping("/items/{productId}")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Remover item do carrinho",
        description = "Remove um produto do carrinho ativo. " +
                      "Equivalente a UpdateCartItemQuantity com quantity = 0, " +
                      "mas com semântica HTTP DELETE explícita.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item removido com sucesso"),
        @ApiResponse(responseCode = "404", description = "Carrinho não encontrado ou item não está no carrinho"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Carrinho não está ativo")
})
public CartResponse removeItem(
        @RequestHeader(value = "X-User-Id", required = false) final String userId,
        @PathVariable final String productId,
        @RequestParam(value = "sessionId", required = false) final String sessionId
) {
    final var command = new RemoveItemFromCartCommand(userId, sessionId, productId);
    return CartResponse.from(
            removeItemFromCartUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> O `sessionId` é passado como **query param** (`?sessionId=sess_abc123`) pois endpoints `DELETE` não devem ter body por convenção REST.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public RemoveItemFromCartUseCase removeItemFromCartUseCase(
        final CartGateway cartGateway,
        final CartActivityLogGateway cartActivityLogGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new RemoveItemFromCartUseCase(
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
| Sem userId e sessionId | erro inline | ambos `null` | `422 Unprocessable Entity` |
| UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | outra transação modificou o carrinho | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `DELETE /api/v1/cart/items/{productId}?sessionId=sess_abc123`

Sem body. Parâmetros via path e query string.

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `productId` | `UUID` (path) | Sim | produto deve estar no carrinho |
| `sessionId` | `string` (query) | Condicional | obrigatório se sem header `X-User-Id` |

**Header:** `X-User-Id: {userId}` — presente para usuários autenticados.

### Response (Sucesso — 200 OK, carrinho com itens restantes)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 99.95,
  "totalItems": 1,
  "items": [
    {
      "cartItemId": "01965f3a-0000-7000-0000-000000000061",
      "productId": "01965f3a-0000-7000-0000-000000000011",
      "quantity": 1,
      "unitPrice": 99.95,
      "subtotal": 99.95
    }
  ],
  "updatedAt": "2026-04-11T15:40:00Z"
}
```

### Response (Sucesso — 200 OK, carrinho vazio após remoção)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 0.00,
  "totalItems": 0,
  "items": [],
  "updatedAt": "2026-04-11T15:40:00Z"
}
```

> Carrinho vazio continua `ACTIVE` — não é convertido nem abandonado automaticamente.

### Response (Erro — 422)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Item não encontrado no carrinho"],
  "timestamp": "2026-04-11T15:40:00Z",
  "path": "/api/v1/cart/items/01965f3a-0000-7000-0000-000000000010"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Cart.removeItem(UUID productId)`** — confirmar que lança `DomainException(ITEM_NOT_FOUND)` e registra `ItemRemovedFromCartEvent` via `registerEvent()`.
2. **`RemoveItemFromCartCommand`** — record simples com 3 campos.
3. **`RemoveItemFromCartOutput`** — mesmo shape dos UCs 87 e 88; considerar extração de `CartOutput` compartilhado.
4. **`RemoveItemFromCartUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
5. **`@Bean removeItemFromCartUseCase`** em `UseCaseConfig`.
6. **Endpoint `DELETE /items/{productId}`** no `CartController` (sem `@RequestBody`, `sessionId` como `@RequestParam`).
7. **Testes unitários** — `RemoveItemFromCartUseCaseTest` com Mockito (sem Spring):
    - item existe → removido, `ItemRemovedFromCartEvent` publicado, log gravado
    - carrinho fica vazio após remoção → `items = []`, `subtotal = 0`, status `ACTIVE`
    - item não encontrado no carrinho (pré-transação) → `Left(notification)` com `ITEM_NOT_FOUND`
    - carrinho não encontrado → `NotFoundException` propagada (404)
    - carrinho não ativo → `DomainException(CART_NOT_ACTIVE)` propagada (422)
    - sem `userId` e sem `sessionId` → `Left(notification)` com erro
    - falha em `activityLogGateway.save()` → rollback de `cartGateway.update()`
    - confirmar que `productGateway` **nunca é chamado**
8. **Testes de integração** (`RemoveItemFromCartIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - linha removida de `cart.cart_items` (deleção física via `orphanRemoval`).
    - `cart.carts.version` incrementado.
    - `cart.cart_activity_log` registra linha com `action = REMOVE_ITEM` e `product_id` correto.
    - Optimistic locking: remoção concorrente do mesmo item → apenas uma transação vence.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-87 AddItemToCart** | Cria os itens que este UC remove |
| **UC-88 UpdateCartItemQuantity** | Alternativa — passa `quantity = 0` para remover implicitamente |
| **UC-90 GetCart** | Leitura do carrinho — mesmo shape de resposta |

### Comparativo UC-88 vs UC-89

| Aspecto | UC-88 `PATCH /items/{productId}` | UC-89 `DELETE /items/{productId}` |
|---|---|---|
| Método HTTP | `PATCH` | `DELETE` |
| Body | `{ "quantity": 0 }` | Sem body |
| `sessionId` passagem | Body JSON | Query param |
| Intenção no log | `UPDATE_ITEM_QUANTITY` ou `REMOVE_ITEM` | Sempre `REMOVE_ITEM` |
| Comportamento | Atualiza **ou** remove | Sempre remove |
