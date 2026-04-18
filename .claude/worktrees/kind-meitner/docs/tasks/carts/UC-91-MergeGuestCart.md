# Task: UC-91 — MergeGuestCart

## 📋 Resumo

Unifica o carrinho anônimo (guest) ao carrinho do usuário autenticado imediatamente após o login. O caso de uso é acionado quando um usuário navega como guest, adiciona produtos ao carrinho e então faz login — seus itens não podem ser perdidos.

**Estratégia de conflito** (quando o mesmo produto existe nos dois carrinhos):
1. Somar as quantidades (`guestQty + userQty`).
2. Verificar o estoque disponível líquido (`product.quantity - reservas ativas`).
3. Se a soma for viável → usar a soma.
4. Se a soma exceder o estoque → usar a maior quantidade viável (o estoque disponível).
5. Ao final, o carrinho guest é marcado como `CONVERTED`.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/cart/merge` deve:

1. Carregar o carrinho guest (`sessionId`) e o carrinho do usuário (`userId`), se existirem.
2. Se não houver carrinho guest → retornar o carrinho do usuário sem alterações (noop).
3. Se não houver carrinho do usuário → associar o carrinho guest ao `userId` diretamente.
4. Se ambos existirem → fazer o merge item a item com a estratégia de conflito descrita.
5. Persistir o carrinho do usuário atualizado e marcar o carrinho guest como `CONVERTED`.
6. Gravar `CartActivityLog` com `action = "MERGE_GUEST"` no carrinho do usuário.
7. Publicar eventos via Outbox.
8. Retornar `200 OK` com o estado do carrinho resultante.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `ALTA (P1)`
* **Endpoint:** `POST /api/v1/cart/merge`
* **Tabelas do Banco:**
  - `cart.carts` — atualização do carrinho do usuário + CONVERT do guest
  - `cart.cart_items` — inserção/atualização de itens no carrinho do usuário
  - `catalog.products` — leitura de estoque disponível (para resolução de conflito)
  - `catalog.stock_reservations` — leitura de reservas ativas (para estoque líquido)

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos já existem. Apenas verificações.

1. **[VERIFICAR]** `domain/.../cart/entity/Cart.java` — confirmar `addItem()`, `updateItemQuantity()`, `associateUser()`, `markAsConverted()`.
2. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()`, `findActiveBySessionId()`, `save()`, `update()`.
3. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar `findById()`.
4. **[VERIFICAR]** `domain/.../catalog/gateway/StockReservationGateway.java` — confirmar `sumActiveQuantityByProduct(ProductId)` para calcular estoque líquido.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/MergeGuestCartCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/MergeGuestCartOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/MergeGuestCartUseCase.java`

### `infrastructure`

> Sem novos arquivos — todos os gateways do cart foram criados no UC-87.

### `api`

1. **[CRIAR]** `api/.../cart/MergeGuestCartRequest.java`
2. **[ALTERAR]** `api/.../cart/CartController.java` — adicionar endpoint `POST /merge`.
3. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean mergeGuestCartUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`MergeGuestCartCommand`**:
```java
package com.btree.application.usecase.cart;

/**
 * Entrada para UC-91 — MergeGuestCart.
 *
 * @param userId    UUID do usuário autenticado (obrigatório)
 * @param sessionId ID da sessão guest cujo carrinho será mesclado
 */
public record MergeGuestCartCommand(
        String userId,
        String sessionId
) {}
```

**`MergeGuestCartOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-91 — MergeGuestCart.
 * Retorna o carrinho resultante do merge com metadados da operação.
 */
public record MergeGuestCartOutput(
        String cartId,
        String status,
        String couponCode,
        BigDecimal subtotal,
        int totalItems,
        List<CartItemOutput> items,
        MergeSummary mergeSummary,
        Instant updatedAt
) {
    /**
     * Resumo do que aconteceu no merge — útil para o frontend
     * exibir alertas de ajuste de quantidade.
     */
    public record MergeSummary(
            int itemsAdded,        // itens do guest adicionados ao carrinho do usuário
            int itemsMerged,       // itens que existiam nos dois carrinhos (conflito resolvido)
            int itemsAdjusted,     // itens cuja quantidade foi reduzida por estoque insuficiente
            boolean guestConverted // true quando o carrinho guest foi marcado como CONVERTED
    ) {}

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

    public static MergeGuestCartOutput from(
            final Cart cart,
            final MergeSummary summary
    ) {
        return new MergeGuestCartOutput(
                cart.getId().getValue().toString(),
                cart.getStatus().name(),
                cart.getCouponCode(),
                cart.subtotal(),
                cart.totalItems(),
                cart.getItems().stream().map(CartItemOutput::from).toList(),
                summary,
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
import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.gateway.CartActivityLogGateway;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockReservationGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;
import static io.vavr.API.Try;

/**
 * UC-91 — MergeGuestCart [CMD P1].
 *
 * <p>Unifica o carrinho guest ao carrinho do usuário autenticado após login.
 *
 * <p><b>Estratégia de conflito</b> (mesmo produto nos dois carrinhos):
 * <ol>
 *   <li>Calcular estoque disponível líquido = {@code product.quantity − reservasAtivas}.</li>
 *   <li>Somar quantidades: {@code merged = guestQty + userQty}.</li>
 *   <li>Se {@code merged ≤ stockDisponivel} → usar a soma.</li>
 *   <li>Se {@code merged > stockDisponivel} → usar {@code min(stockDisponivel, max(guestQty, userQty))}.</li>
 * </ol>
 *
 * <p><b>Casos especiais:</b>
 * <ul>
 *   <li>Sem carrinho guest → retornar carrinho do usuário sem alteração (noop).</li>
 *   <li>Sem carrinho do usuário → associar o guest ao userId ({@code cart.associateUser()}) e retornar.</li>
 *   <li>Ambos existem → merge completo com resolução de conflito.</li>
 * </ul>
 */
public class MergeGuestCartUseCase implements UseCase<MergeGuestCartCommand, MergeGuestCartOutput> {

    private final CartGateway               cartGateway;
    private final CartActivityLogGateway    activityLogGateway;
    private final ProductGateway            productGateway;
    private final StockReservationGateway   reservationGateway;
    private final DomainEventPublisher      eventPublisher;
    private final TransactionManager        transactionManager;

    public MergeGuestCartUseCase(
            final CartGateway cartGateway,
            final CartActivityLogGateway activityLogGateway,
            final ProductGateway productGateway,
            final StockReservationGateway reservationGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.cartGateway        = cartGateway;
        this.activityLogGateway = activityLogGateway;
        this.productGateway     = productGateway;
        this.reservationGateway = reservationGateway;
        this.eventPublisher     = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, MergeGuestCartOutput> execute(final MergeGuestCartCommand command) {

        // 1. Validar entrada
        final var notification = Notification.create();
        if (command.userId() == null || command.userId().isBlank()) {
            notification.append(new com.btree.shared.validation.Error("'userId' é obrigatório"));
        }
        if (command.sessionId() == null || command.sessionId().isBlank()) {
            notification.append(new com.btree.shared.validation.Error("'sessionId' é obrigatório"));
        }
        if (notification.hasError()) {
            return Left(notification);
        }

        // 2. Parsear userId
        final UUID userId;
        try {
            userId = UUID.fromString(command.userId());
        } catch (final IllegalArgumentException e) {
            notification.append(new com.btree.shared.validation.Error("'userId' não é um UUID válido"));
            return Left(notification);
        }

        // 3. Carregar carrinhos
        final var guestCartOpt = cartGateway.findActiveBySessionId(command.sessionId());
        final var userCartOpt  = cartGateway.findActiveByUserId(userId);

        // 3a. Caso trivial: sem carrinho guest → retornar carrinho do usuário sem alteração
        if (guestCartOpt.isEmpty()) {
            final var userCart = userCartOpt.orElseThrow(
                    () -> new com.btree.shared.exception.NotFoundException(CartError.CART_NOT_FOUND)
            );
            final var noop = new MergeGuestCartOutput.MergeSummary(0, 0, 0, false);
            return Right(MergeGuestCartOutput.from(userCart, noop));
        }

        final var guestCart = guestCartOpt.get();

        // 3b. Caso simples: sem carrinho do usuário → associar o guest ao userId
        if (userCartOpt.isEmpty()) {
            return Try(() -> transactionManager.execute(() -> {
                guestCart.associateUser(userId);
                final var savedCart = cartGateway.update(guestCart);
                eventPublisher.publishAll(savedCart.getDomainEvents());
                final var summary = new MergeGuestCartOutput.MergeSummary(
                        guestCart.getItems().size(), 0, 0, false);
                return MergeGuestCartOutput.from(savedCart, summary);
            })).toEither().mapLeft(Notification::create);
        }

        // 4. Caso completo: ambos existem → merge com resolução de conflito
        final var userCart = userCartOpt.get();

        // Contadores para o MergeSummary
        final int[] counters = {0, 0, 0}; // [added, merged, adjusted]

        for (final CartItem guestItem : guestCart.getItems()) {
            final var productId = guestItem.getProductId();

            // Verificar se o produto já está no carrinho do usuário
            final var existingUserItem = userCart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst();

            if (existingUserItem.isEmpty()) {
                // Produto somente no guest → adicionar ao carrinho do usuário
                userCart.addItem(productId, guestItem.getQuantity(), guestItem.getUnitPrice());
                counters[0]++; // added
            } else {
                // Produto em ambos → resolver conflito
                final int userQty  = existingUserItem.get().getQuantity();
                final int guestQty = guestItem.getQuantity();
                final int desiredQty = userQty + guestQty;

                // Calcular estoque disponível líquido
                final int availableStock = calculateAvailableStock(productId);

                final int finalQty;
                if (desiredQty <= availableStock) {
                    finalQty = desiredQty;
                    counters[1]++; // merged
                } else {
                    // Usar a maior quantidade viável dentro do limite de estoque
                    finalQty = Math.min(availableStock, Math.max(userQty, guestQty));
                    counters[1]++; // merged
                    if (finalQty < desiredQty) {
                        counters[2]++; // adjusted
                    }
                }

                userCart.updateItemQuantity(productId, finalQty);
            }
        }

        // 5. Persistir atomicamente
        return Try(() -> transactionManager.execute(() -> {

            // 5a. Persistir carrinho do usuário atualizado
            final var updatedUserCart = cartGateway.update(userCart);

            // 5b. Marcar carrinho guest como CONVERTED
            guestCart.markAsConverted();
            cartGateway.update(guestCart);

            // 5c. Gravar audit log
            final var log = CartActivityLog.create(
                    updatedUserCart.getId(),
                    "MERGE_GUEST",
                    null,
                    guestCart.getItems().size(),
                    null
            );
            activityLogGateway.save(log);

            // 5d. Publicar eventos do carrinho do usuário e do guest
            eventPublisher.publishAll(updatedUserCart.getDomainEvents());
            eventPublisher.publishAll(guestCart.getDomainEvents());

            final var summary = new MergeGuestCartOutput.MergeSummary(
                    counters[0], counters[1], counters[2], true);

            return MergeGuestCartOutput.from(updatedUserCart, summary);
        })).toEither().mapLeft(Notification::create);
    }

    /**
     * Calcula o estoque disponível líquido:
     * {@code product.quantity − reservasAtivas}.
     * Retorna 0 se o produto não for encontrado.
     */
    private int calculateAvailableStock(final UUID productId) {
        final var pid = ProductId.from(productId);
        return productGateway.findById(pid)
                .map(p -> {
                    final int reserved = reservationGateway.sumActiveQuantityByProduct(pid);
                    return Math.max(0, p.getQuantity() - reserved);
                })
                .orElse(0);
    }
}
```

> **Nota sobre transação e loop de I/O**: o `calculateAvailableStock()` é chamado **fora** da transação principal para evitar locks desnecessários. As leituras de estoque são informativas para resolução de conflito — não garantem exclusividade. Se precisar de garantia forte, mover para dentro do `transactionManager.execute()`.

### 3. Roteamento e Injeção (API)

**`MergeGuestCartRequest`**:
```java
package com.btree.api.cart;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body para POST /api/v1/cart/merge.
 */
public record MergeGuestCartRequest(

        @NotBlank(message = "'sessionId' é obrigatório")
        String sessionId
) {}
```

**Endpoint no `CartController`**:
```java
@PostMapping("/merge")
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Unir carrinho guest ao carrinho autenticado",
        description = "Mescla o carrinho da sessão anônima ao carrinho do usuário logado. " +
                      "Conflitos (mesmo produto nos dois carrinhos) somam quantidades respeitando " +
                      "o estoque disponível. O carrinho guest é marcado como CONVERTED ao final.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Merge realizado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
        @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
        @ApiResponse(responseCode = "422", description = "sessionId ou userId inválido")
})
public MergeGuestCartResponse mergeGuestCart(
        @RequestHeader("X-User-Id") final String userId,   // obrigatório — endpoint autenticado
        @Valid @RequestBody final MergeGuestCartRequest request
) {
    final var command = new MergeGuestCartCommand(userId, request.sessionId());
    return MergeGuestCartResponse.from(
            mergeGuestCartUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`MergeGuestCartResponse`**:
```java
package com.btree.api.cart;

import com.btree.application.usecase.cart.MergeGuestCartOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MergeGuestCartResponse(
        String cartId,
        String status,
        String couponCode,
        BigDecimal subtotal,
        int totalItems,
        List<CartItemResponse> items,
        MergeSummaryResponse mergeSummary,
        Instant updatedAt
) {
    public record CartItemResponse(
            String cartItemId, String productId,
            int quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record MergeSummaryResponse(
            int itemsAdded, int itemsMerged, int itemsAdjusted, boolean guestConverted
    ) {}

    public static MergeGuestCartResponse from(final MergeGuestCartOutput output) {
        return new MergeGuestCartResponse(
                output.cartId(), output.status(), output.couponCode(),
                output.subtotal(), output.totalItems(),
                output.items().stream()
                        .map(i -> new CartItemResponse(
                                i.cartItemId(), i.productId(),
                                i.quantity(), i.unitPrice(), i.subtotal()))
                        .toList(),
                new MergeSummaryResponse(
                        output.mergeSummary().itemsAdded(),
                        output.mergeSummary().itemsMerged(),
                        output.mergeSummary().itemsAdjusted(),
                        output.mergeSummary().guestConverted()),
                output.updatedAt()
        );
    }
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public MergeGuestCartUseCase mergeGuestCartUseCase(
        final CartGateway cartGateway,
        final CartActivityLogGateway cartActivityLogGateway,
        final ProductGateway productGateway,
        final StockReservationGateway stockReservationGateway,
        final DomainEventPublisher eventPublisher,
        final TransactionManager transactionManager
) {
    return new MergeGuestCartUseCase(
            cartGateway,
            cartActivityLogGateway,
            productGateway,
            stockReservationGateway,
            eventPublisher,
            transactionManager
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| `userId` ausente ou vazio | erro inline | `command.userId() == null` | `422 Unprocessable Entity` |
| `sessionId` ausente ou vazio | erro inline | `command.sessionId() == null` | `422 Unprocessable Entity` |
| `userId` UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |
| Sem carrinho guest nem de usuário | `NotFoundException` | ambos `Optional.empty()` | `404 Not Found` |
| Carrinho não ativo | `CartError.CART_NOT_ACTIVE` | aggregate `assertActive()` lança `DomainException` | `422 Unprocessable Entity` |
| Conflito de versão | `ObjectOptimisticLockingFailureException` | outra transação modificou o carrinho | `409 Conflict` |

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/cart/merge`

```json
{
  "sessionId": "sess_abc123"
}
```

**Header obrigatório:** `X-User-Id: {userId}` — endpoint exige autenticação.

### Response (Sucesso — 200 OK, merge com ajuste de quantidade)

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
  "mergeSummary": {
    "itemsAdded": 1,
    "itemsMerged": 1,
    "itemsAdjusted": 1,
    "guestConverted": true
  },
  "updatedAt": "2026-04-11T16:00:00Z"
}
```

> `itemsAdjusted > 0` → frontend deve exibir alerta: *"Quantidade de alguns itens foi ajustada por limite de estoque."*

### Response (Sucesso — 200 OK, sem carrinho guest — noop)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "subtotal": 99.95,
  "totalItems": 1,
  "items": [...],
  "mergeSummary": {
    "itemsAdded": 0,
    "itemsMerged": 0,
    "itemsAdjusted": 0,
    "guestConverted": false
  },
  "updatedAt": "2026-04-11T16:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Cart.associateUser(UUID userId)`** — confirmar que atualiza `this.userId` e chama `incrementVersion()`.
2. **Verificar `Cart.markAsConverted()`** — confirmar que registra `CartConvertedEvent`.
3. **Verificar `StockReservationGateway.sumActiveQuantityByProduct(ProductId)`** — confirmar implementação no `StockReservationPostgresGateway`.
4. **`MergeGuestCartCommand`** — record com `userId` e `sessionId`.
5. **`MergeGuestCartOutput`** — record com nested `MergeSummary` e `CartItemOutput`. Factory `from(Cart, MergeSummary)`.
6. **`MergeGuestCartUseCase`** — lógica dos 3 casos (noop / associar / merge completo).
7. **`@Bean mergeGuestCartUseCase`** em `UseCaseConfig`.
8. **`MergeGuestCartRequest`** — record com `@NotBlank sessionId`.
9. **`MergeGuestCartResponse`** + **`MergeGuestCartResponse.MergeSummaryResponse`**.
10. **Endpoint `POST /merge`** no `CartController`.
11. **Testes unitários** — `MergeGuestCartUseCaseTest` com Mockito (sem Spring):
    - sem carrinho guest → noop, carrinho do usuário retornado sem alteração
    - sem carrinho do usuário → guest associado ao userId, `associateUser()` chamado
    - produto somente no guest → adicionado ao carrinho do usuário (`itemsAdded++`)
    - produto em ambos, soma ≤ estoque → quantidade somada (`itemsMerged++`, `itemsAdjusted == 0`)
    - produto em ambos, soma > estoque → quantidade ajustada para o máximo viável (`itemsAdjusted++`)
    - estoque zerado → usar a maior das duas quantidades (`max(guestQty, userQty)`)
    - guest marcado como `CONVERTED` após merge completo (`guestConverted = true`)
    - `userId` ausente → `Left(notification)` com erro
    - `sessionId` ausente → `Left(notification)` com erro
    - falha em `cartGateway.update()` → rollback completo
12. **Testes de integração** (`MergeGuestCartIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - Carrinho guest com `status = CONVERTED` após merge.
    - Itens do guest transferidos corretamente ao carrinho do usuário.
    - Conflito resolvido corretamente com estoque real.
    - Rollback se qualquer write falhar.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-87 AddItemToCart** | Cria os carrinhos guest e de usuário mesclados neste UC |
| **UC-90 GetCart** | Chamado após o merge para exibir o carrinho resultante |
| **UC-71 ReserveStock** | Usa `sumActiveQuantityByProduct()` para calcular o mesmo estoque líquido |

---

## 💡 Considerações de Design

### Por que usar `max(userQty, guestQty)` e não apenas `userQty`?
Quando o estoque é insuficiente para a soma, preferimos não penalizar o usuário descartando itens que ele já tinha no guest. A regra `max()` mantém a intenção original de adicionar mais do que já havia, usando o maior valor viável.

### Quando chamar este UC?
Deve ser invocado pelo **frontend imediatamente após o login** — preferencialmente no mesmo request de resposta do `AuthenticateUser`, ou em um request subsequente antes de redirecionar para o carrinho. O `sessionId` deve ser o cookie/header da sessão anônima.

### Idempotência
Se chamado múltiplas vezes com o mesmo `sessionId` após o primeiro merge (guest já `CONVERTED`), `findActiveBySessionId()` retornará `Optional.empty()` → noop automático. O UC é naturalmente idempotente.
