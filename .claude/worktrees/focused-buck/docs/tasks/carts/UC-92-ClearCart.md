# Task: UC-92 — ClearCart

## 📋 Resumo

Esvazia completamente o carrinho ativo do usuário, removendo todos os itens de uma só vez. É mais eficiente que chamar `RemoveItemFromCart` (UC-89) repetidamente para cada item e fornece uma semântica explícita de "recomeçar o carrinho". O carrinho permanece `ACTIVE` e disponível para receber novos itens após ser esvaziado.

## 🎯 Objetivo

Ao final da implementação, o endpoint `DELETE /api/v1/cart/items` deve:

1. Localizar o carrinho ativo do usuário (`userId` ou `sessionId`).
2. Verificar que o carrinho não está vazio (sem itens → 422).
3. Chamar `cart.clearItems()` no aggregate — remove todos os itens em lote.
4. Persistir via `cartGateway.update()`.
5. Gravar `CartActivityLog` com `action = "CLEAR_CART"`.
6. Publicar eventos de domínio via Outbox.
7. Retornar `200 OK` com o carrinho vazio.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `ALTA (P1)`
* **Endpoint:** `DELETE /api/v1/cart/items`
* **Tabelas do Banco:**
  - `cart.cart_items` — deleção física de todas as linhas do carrinho (via `orphanRemoval`)
  - `cart.carts` — atualização de `updated_at` e `version`
  - `cart.cart_activity_log` — registro da ação

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. **[CRIAR]** `domain/.../cart/entity/Cart.java` — adicionar o behavior `clearItems()` se não existir (veja algoritmo abaixo).

> Verificar antes de criar: `grep -n "clearItems\|clear\|removeAll" Cart.java`

2. **[VERIFICAR]** `domain/.../cart/error/CartError.java` — confirmar `CART_NOT_ACTIVE` e `CART_EMPTY`.
3. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()`, `findActiveBySessionId()`, `update()`.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/ClearCartCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/ClearCartOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/ClearCartUseCase.java`

### `infrastructure`

> Sem novos arquivos — tudo criado no UC-87.

### `api`

1. **[ALTERAR]** `api/.../cart/CartController.java` — adicionar endpoint `DELETE /items`.
2. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean clearCartUseCase`.

> Sem `Request` record — `DELETE /api/v1/cart/items` sem body. `sessionId` como query param (guests).

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Behavior no Aggregate `Cart` (Domain)

Se `clearItems()` não existir, adicionar ao `Cart.java`:

```java
// Cart.java — behavior a adicionar
/**
 * Remove todos os itens do carrinho de uma só vez.
 * O carrinho permanece ACTIVE e disponível para novos itens.
 *
 * @throws DomainException se o carrinho não estiver ACTIVE
 */
public void clearItems() {
    assertActive();
    if (this.items.isEmpty()) {
        throw DomainException.with(CartError.CART_EMPTY);
    }
    this.items.clear();
    this.updatedAt = Instant.now();
    incrementVersion();
    // Não registra ItemRemovedFromCartEvent por item — registra apenas via ActivityLog
}
```

> Se preferir publicar um evento de domínio para o clear, criar um `CartClearedEvent` análogo ao `CartCreatedEvent`. Para o MVP, registrar apenas o `CartActivityLog` é suficiente.

### 2. Command e Output (Application)

**`ClearCartCommand`**:
```java
package com.btree.application.usecase.cart;

/**
 * Entrada para UC-92 — ClearCart.
 *
 * @param userId    UUID do usuário autenticado (null se guest)
 * @param sessionId ID de sessão do guest (null se autenticado)
 */
public record ClearCartCommand(
        String userId,
        String sessionId
) {}
```

**`ClearCartOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-92 — ClearCart.
 * Retorna o carrinho vazio após a operação.
 */
public record ClearCartOutput(
        String cartId,
        String status,
        BigDecimal subtotal,
        int totalItems,
        List<Object> items,   // sempre vazio após clear
        Instant updatedAt
) {
    public static ClearCartOutput from(final Cart cart) {
        return new ClearCartOutput(
                cart.getId().getValue().toString(),
                cart.getStatus().name(),
                cart.subtotal(),         // 0.00 após clear
                cart.totalItems(),       // 0 após clear
                List.of(),
                cart.getUpdatedAt()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.error.CartError;
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
 * UC-92 — ClearCart [CMD P1].
 *
 * <p>Esvazia completamente o carrinho ativo do usuário em uma operação atômica.
 * O carrinho permanece {@code ACTIVE} após ser esvaziado.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Localizar carrinho ativo — {@code NotFoundException} se ausente.</li>
 *   <li>Verificar que não está vazio antes da transação.</li>
 *   <li>Chamar {@code cart.clearItems()} — remove todos os itens do aggregate.</li>
 *   <li>Persistir, gravar log e publicar eventos.</li>
 * </ol>
 */
public class ClearCartUseCase implements UseCase<ClearCartCommand, ClearCartOutput> {

    private final CartGateway            cartGateway;
    private final CartActivityLogGateway activityLogGateway;
    private final DomainEventPublisher   eventPublisher;
    private final TransactionManager     transactionManager;

    public ClearCartUseCase(
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
    public Either<Notification, ClearCartOutput> execute(final ClearCartCommand command) {

        // 1. Validar identificação
        final var notification = Notification.create();

        if (command.userId() == null && command.sessionId() == null) {
            notification.append(new com.btree.shared.validation.Error(
                    "'userId' ou 'sessionId' é obrigatório"));
            return Left(notification);
        }

        // 2. Parsear userId
        final UUID userId;
        try {
            userId = command.userId() != null ? UUID.fromString(command.userId()) : null;
        } catch (final IllegalArgumentException e) {
            notification.append(new com.btree.shared.validation.Error("'userId' não é um UUID válido"));
            return Left(notification);
        }

        // 3. Localizar carrinho ativo — NotFoundException propaga como 404
        final var cart = (userId != null
                ? cartGateway.findActiveByUserId(userId)
                : cartGateway.findActiveBySessionId(command.sessionId()))
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND));

        // 4. Verificar que o carrinho não está vazio antes de entrar na transação
        if (cart.isEmpty()) {
            notification.append(CartError.CART_EMPTY);
            return Left(notification);
        }

        // 5. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 5a. Remover todos os itens via aggregate
            cart.clearItems();
            final var updatedCart = cartGateway.update(cart);

            // 5b. Gravar audit log
            final var log = CartActivityLog.create(
                    updatedCart.getId(),
                    "CLEAR_CART",
                    null,
                    null,
                    null
            );
            activityLogGateway.save(log);

            // 5c. Publicar domain events (se clearItems() registrar algum evento)
            eventPublisher.publishAll(updatedCart.getDomainEvents());

            return ClearCartOutput.from(updatedCart);
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 4. Roteamento e Injeção (API)

**Endpoint no `CartController`**:
```java
@DeleteMapping("/items")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Esvaziar carrinho",
        description = "Remove todos os itens do carrinho ativo de uma só vez. " +
                      "O carrinho permanece ACTIVE e pode receber novos itens.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Carrinho esvaziado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Carrinho não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "Carrinho não ativo ou já vazio")
})
public CartResponse clearCart(
        @RequestHeader(value = "X-User-Id", required = false) final String userId,
        @RequestParam(value = "sessionId", required = false) final String sessionId
) {
    final var command = new ClearCartCommand(userId, sessionId);
    return CartResponse.from(
            clearCartUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

> **Diferença crítica de rotas:**
> - `DELETE /api/v1/cart/items` → **ClearCart** (esvazia todos — UC-92)
> - `DELETE /api/v1/cart/items/{productId}` → **RemoveItemFromCart** (remove um — UC-89)
>
> O Spring distingue automaticamente pelos templates de path. Confirmar que não há ambiguidade no `CartController`.

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public ClearCartUseCase clearCartUseCase(
        final CartGateway cartGateway,
        final CartActivityLogGateway cartActivityLogGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new ClearCartUseCase(
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
| Carrinho já vazio | `CartError.CART_EMPTY` | `cart.isEmpty() == true` | `422 Unprocessable Entity` |
| Carrinho não ativo | `CartError.CART_NOT_ACTIVE` | aggregate `assertActive()` lança `DomainException` | `422 Unprocessable Entity` |
| Sem userId e sessionId | erro inline | ambos `null` | `422 Unprocessable Entity` |
| `userId` UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | outra transação modificou o carrinho | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `DELETE /api/v1/cart/items?sessionId=sess_abc123`

Sem body.

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `sessionId` | `string` (query) | Condicional | obrigatório se sem header `X-User-Id` |

**Header:** `X-User-Id: {userId}` — presente para usuários autenticados.

### Response (Sucesso — 200 OK)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 0.00,
  "totalItems": 0,
  "items": [],
  "updatedAt": "2026-04-11T16:10:00Z"
}
```

### Response (Erro — 422, carrinho já vazio)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": ["Carrinho não possui itens"],
  "timestamp": "2026-04-11T16:10:00Z",
  "path": "/api/v1/cart/items"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Adicionar `Cart.clearItems()`** em `Cart.java` — verificar se já existe, criar se não existir. Deve chamar `assertActive()`, verificar `isEmpty()` e chamar `this.items.clear()`.
2. **Confirmar `CartError.CART_EMPTY`** — já existe na classe `CartError`.
3. **`ClearCartCommand`** — record com `userId` e `sessionId`.
4. **`ClearCartOutput`** — record simples; factory `from(Cart)`.
5. **`ClearCartUseCase`** — lógica com `Notification` + `transactionManager.execute()`.
6. **`@Bean clearCartUseCase`** em `UseCaseConfig`.
7. **Endpoint `DELETE /items`** no `CartController` — confirmar que não há conflito com `DELETE /items/{productId}` do UC-89.
8. **Testes unitários** — `ClearCartUseCaseTest` com Mockito (sem Spring):
    - carrinho com itens → esvaziado, log gravado, eventos publicados, `items = []`, `subtotal = 0`
    - carrinho vazio → `Left(notification)` com `CART_EMPTY` (pré-transação, sem I/O de escrita)
    - carrinho não encontrado → `NotFoundException` propagada (404)
    - carrinho não ativo → `DomainException(CART_NOT_ACTIVE)` propagada (422)
    - sem `userId` e sem `sessionId` → `Left(notification)` com erro
    - falha em `activityLogGateway.save()` → rollback de `cartGateway.update()`
    - confirmar que `productGateway` **nunca é chamado**
9. **Testes de integração** (`ClearCartIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - `cart.cart_items` com zero linhas para o `cart_id` após o clear.
    - `cart.carts.version` incrementado.
    - `cart.carts.status` permanece `ACTIVE` (não muda para `ABANDONED`).
    - `cart.cart_activity_log` registra linha com `action = CLEAR_CART`.
    - Rollback se `activityLogGateway.save()` falhar.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-87 AddItemToCart** | Cria os itens que este UC remove em lote |
| **UC-89 RemoveItemFromCart** | Alternativa granular — remove apenas um item por vez |
| **UC-90 GetCart** | Após o clear, exibe carrinho vazio (`items = []`) |

### Comparativo DELETE /items vs DELETE /items/{productId}

| Aspecto | UC-89 `DELETE /items/{productId}` | UC-92 `DELETE /items` |
|---|---|---|
| Escopo | Remove **um** produto específico | Remove **todos** os itens |
| Path param | `{productId}` obrigatório | Sem path param |
| Erro se vazio | Item não encontrado | Carrinho já vazio |
| Evento no aggregate | `ItemRemovedFromCartEvent` | Nenhum (ou `CartClearedEvent` opcional) |
| Activity log action | `REMOVE_ITEM` | `CLEAR_CART` |
