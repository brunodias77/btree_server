# Task: UC-90 — GetCart

## 📋 Resumo

Retorna o carrinho ativo completo do usuário ou sessão, enriquecido com os **preços atuais** de catálogo para cada item. Detecta automaticamente discrepâncias entre o `unitPrice` armazenado no item e o preço atual do produto, sinalizando itens com preço desatualizado para que o frontend possa alertar o usuário. Operação somente leitura — sem escrita em nenhuma tabela.

## 🎯 Objetivo

Ao final da implementação, o endpoint `GET /api/v1/cart` deve:

1. Localizar o carrinho ativo do usuário (`userId`) ou sessão (`sessionId`).
2. Para cada item do carrinho, buscar o produto correspondente em `catalog.products`.
3. Comparar o `unitPrice` armazenado com o `price` atual do produto.
4. Retornar `200 OK` com o carrinho completo, incluindo:
   - Preço armazenado (`unitPrice`) e preço atual (`currentPrice`) por item.
   - Flag `priceChanged = true` se os preços diferirem.
   - Subtotal calculado com os preços **armazenados** (o usuário deve ver o que aceitou).
5. Se não houver carrinho ativo, retornar `404 Not Found`.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Tipo:** `[QRY]` — implements `QueryUseCase<Command, Output>` — somente leitura
* **Endpoint:** `GET /api/v1/cart`
* **Tabelas do Banco:**
  - `cart.carts` — leitura do carrinho ativo (`readOnly = true`)
  - `cart.cart_items` — leitura dos itens (`readOnly = true`)
  - `catalog.products` — leitura de preço e status atuais (`readOnly = true`)

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos já existem. Apenas verificações.

1. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()` e `findActiveBySessionId()`.
2. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar `findById(ProductId)`.

### `application`

1. **[CRIAR]** `application/.../usecase/cart/GetCartCommand.java`
2. **[CRIAR]** `application/.../usecase/cart/GetCartOutput.java`
3. **[CRIAR]** `application/.../usecase/cart/GetCartUseCase.java`

### `infrastructure`

> Sem novos arquivos. Todos os gateways do cart foram criados no UC-87.

1. **[VERIFICAR]** `infrastructure/.../cart/persistence/CartPostgresGateway.java` — confirmar `@Transactional(readOnly = true)` nos métodos de leitura.

### `api`

1. **[CRIAR]** `api/.../cart/GetCartResponse.java`
2. **[ALTERAR]** `api/.../cart/CartController.java` — adicionar endpoint `GET /`.
3. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean getCartUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`GetCartCommand`**:
```java
package com.btree.application.usecase.cart;

/**
 * Entrada para UC-90 — GetCart.
 *
 * @param userId    UUID do usuário autenticado (null se guest)
 * @param sessionId ID de sessão do guest (null se autenticado)
 */
public record GetCartCommand(
        String userId,
        String sessionId
) {}
```

**`GetCartOutput`**:
```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.entity.Cart;
import com.btree.domain.cart.entity.CartItem;
import com.btree.domain.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saída para UC-90 — GetCart.
 *
 * <p>Inclui preço atual de catálogo por item para detecção de variação de preço.
 */
public record GetCartOutput(
        String cartId,
        String status,
        String couponCode,
        String shippingMethod,
        BigDecimal subtotal,
        int totalItems,
        boolean hasPriceChanges,
        List<CartItemOutput> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public record CartItemOutput(
            String cartItemId,
            String productId,
            String productName,
            String productStatus,
            int quantity,
            BigDecimal unitPrice,       // preço armazenado no momento da adição
            BigDecimal currentPrice,    // preço atual do catálogo
            boolean priceChanged,       // true se currentPrice != unitPrice
            BigDecimal subtotal
    ) {}

    /**
     * Monta o output enriquecendo cada item com o preço atual do produto.
     *
     * @param cart     carrinho ativo
     * @param products mapa de ProductId → Product para lookup eficiente (sem N+1)
     */
    public static GetCartOutput from(
            final Cart cart,
            final Map<UUID, Product> products
    ) {
        final var itemOutputs = cart.getItems().stream()
                .map(item -> {
                    final var product = products.get(item.getProductId());
                    final BigDecimal currentPrice = product != null
                            ? product.getPrice()
                            : item.getUnitPrice(); // fallback se produto não encontrado
                    final boolean priceChanged =
                            item.getUnitPrice().compareTo(currentPrice) != 0;
                    final String productName = product != null ? product.getName() : "Produto indisponível";
                    final String productStatus = product != null ? product.getStatus().name() : "UNKNOWN";

                    return new CartItemOutput(
                            item.getId().getValue().toString(),
                            item.getProductId().toString(),
                            productName,
                            productStatus,
                            item.getQuantity(),
                            item.getUnitPrice(),
                            currentPrice,
                            priceChanged,
                            item.subtotal()
                    );
                })
                .toList();

        final boolean hasPriceChanges = itemOutputs.stream()
                .anyMatch(CartItemOutput::priceChanged);

        return new GetCartOutput(
                cart.getId().getValue().toString(),
                cart.getStatus().name(),
                cart.getCouponCode(),
                cart.getShippingMethod() != null ? cart.getShippingMethod().name() : null,
                cart.subtotal(),
                cart.totalItems(),
                hasPriceChanges,
                itemOutputs,
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getExpiresAt()
        );
    }
}
```

### 2. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.cart;

import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

/**
 * UC-90 — GetCart [QRY P0].
 *
 * <p>Retorna o carrinho ativo com preços atuais de catálogo.
 * Detecta variações de preço desde a adição do item.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Localizar carrinho ativo — {@code NotFoundException} se ausente.</li>
 *   <li>Coletar todos os {@code productId}s dos itens.</li>
 *   <li>Buscar produtos em lote (evitar N+1 queries).</li>
 *   <li>Construir output com enrichment de preço e flag {@code priceChanged}.</li>
 * </ol>
 *
 * <p>Operação somente leitura — sem escrita, sem {@code TransactionManager}.
 */
public class GetCartUseCase implements QueryUseCase<GetCartCommand, GetCartOutput> {

    private final CartGateway    cartGateway;
    private final ProductGateway productGateway;

    public GetCartUseCase(
            final CartGateway cartGateway,
            final ProductGateway productGateway
    ) {
        this.cartGateway    = cartGateway;
        this.productGateway = productGateway;
    }

    @Override
    public Either<Notification, GetCartOutput> execute(final GetCartCommand command) {

        // 1. Validar identificação
        if (command.userId() == null && command.sessionId() == null) {
            final var notification = Notification.create();
            notification.append(new com.btree.shared.validation.Error(
                    "'userId' ou 'sessionId' é obrigatório"));
            return Left(notification);
        }

        // 2. Parsear userId
        final UUID userId;
        try {
            userId = command.userId() != null ? UUID.fromString(command.userId()) : null;
        } catch (final IllegalArgumentException e) {
            final var notification = Notification.create();
            notification.append(new com.btree.shared.validation.Error("'userId' não é um UUID válido"));
            return Left(notification);
        }

        // 3. Localizar carrinho ativo — NotFoundException propaga como 404
        final var cart = (userId != null
                ? cartGateway.findActiveByUserId(userId)
                : cartGateway.findActiveBySessionId(command.sessionId()))
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND));

        // 4. Buscar preços atuais em lote (um findById por item — sem N+1 via stream paralelo)
        //    Se o ProductGateway tiver um findAllByIds(), prefira usá-lo.
        final Map<UUID, Product> products = cart.getItems().stream()
                .map(item -> productGateway.findById(ProductId.from(item.getProductId())))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .collect(Collectors.toMap(
                        p -> p.getId().getValue(),
                        p -> p
                ));

        // 5. Construir output
        return Right(GetCartOutput.from(cart, products));
    }
}
```

> **Performance — N+1 queries**: o algoritmo acima faz um `findById()` por item. Para carrinhos com muitos itens distintos, considerar adicionar `findAllByIds(List<ProductId>)` ao `ProductGateway` e implementar via `repository.findAllById(ids)` no gateway. Para o caso de uso atual (carrinho típico de 1–20 itens), o impacto é aceitável.

### 3. Roteamento e Injeção (API)

**`GetCartResponse`**:
```java
package com.btree.api.cart;

import com.btree.application.usecase.cart.GetCartOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GetCartResponse(
        String cartId,
        String status,
        String couponCode,
        String shippingMethod,
        BigDecimal subtotal,
        int totalItems,
        boolean hasPriceChanges,
        List<CartItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public record CartItemResponse(
            String cartItemId,
            String productId,
            String productName,
            String productStatus,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal currentPrice,
            boolean priceChanged,
            BigDecimal subtotal
    ) {}

    public static GetCartResponse from(final GetCartOutput output) {
        return new GetCartResponse(
                output.cartId(),
                output.status(),
                output.couponCode(),
                output.shippingMethod(),
                output.subtotal(),
                output.totalItems(),
                output.hasPriceChanges(),
                output.items().stream()
                        .map(i -> new CartItemResponse(
                                i.cartItemId(), i.productId(),
                                i.productName(), i.productStatus(),
                                i.quantity(), i.unitPrice(),
                                i.currentPrice(), i.priceChanged(),
                                i.subtotal()))
                        .toList(),
                output.createdAt(),
                output.updatedAt(),
                output.expiresAt()
        );
    }
}
```

**Endpoint no `CartController`**:
```java
@GetMapping
@ResponseStatus(HttpStatus.OK)
@Operation(
        summary = "Obter carrinho ativo",
        description = "Retorna o carrinho ativo do usuário com preços atuais de catálogo. " +
                      "Sinaliza itens com preço alterado desde a adição via 'priceChanged = true'.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Carrinho retornado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Nenhum carrinho ativo encontrado"),
        @ApiResponse(responseCode = "422", description = "Identificação inválida")
})
public GetCartResponse getCart(
        @RequestHeader(value = "X-User-Id", required = false) final String userId,
        @RequestParam(value = "sessionId", required = false) final String sessionId
) {
    final var command = new GetCartCommand(userId, sessionId);
    return GetCartResponse.from(
            getCartUseCase.execute(command)
                    .getOrElseThrow(n -> DomainException.with(n.getErrors()))
    );
}
```

**`UseCaseConfig.java`** — adicionar bean (sem `TransactionManager` — somente leitura):
```java
@Bean
public GetCartUseCase getCartUseCase(
        final CartGateway cartGateway,
        final ProductGateway productGateway
) {
    return new GetCartUseCase(cartGateway, productGateway);
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| Carrinho não encontrado | `NotFoundException` (lançada diretamente) | Sem carrinho ativo para o userId/sessionId | `404 Not Found` |
| Sem userId e sessionId | erro inline | ambos `null` | `422 Unprocessable Entity` |
| `userId` UUID malformado | erro inline | `UUID.fromString()` lança exceção | `422 Unprocessable Entity` |

> **Produto não encontrado em catálogo**: se um produto do carrinho foi deletado ou não existir mais no catálogo, o código usa fallback gracioso (`productName = "Produto indisponível"`, `productStatus = "UNKNOWN"`, `priceChanged = false`). **Não lançar NotFoundException** — o carrinho deve sempre ser retornável mesmo se produtos foram removidos do catálogo.

---

## 🌐 Contrato da API REST

### Request — `GET /api/v1/cart?sessionId=sess_abc123`

Sem body.

| Parâmetro | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `sessionId` | `string` (query) | Condicional | obrigatório se sem header `X-User-Id` |

**Header:** `X-User-Id: {userId}` — presente para usuários autenticados.

### Response (Sucesso — 200 OK, com variação de preço)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "shippingMethod": null,
  "subtotal": 199.90,
  "totalItems": 2,
  "hasPriceChanges": true,
  "items": [
    {
      "cartItemId": "01965f3a-0000-7000-0000-000000000060",
      "productId": "01965f3a-0000-7000-0000-000000000010",
      "productName": "Tênis Running Pro",
      "productStatus": "ACTIVE",
      "quantity": 2,
      "unitPrice": 99.95,
      "currentPrice": 119.95,
      "priceChanged": true,
      "subtotal": 199.90
    }
  ],
  "createdAt": "2026-04-11T14:00:00Z",
  "updatedAt": "2026-04-11T15:00:00Z",
  "expiresAt": "2026-04-12T14:00:00Z"
}
```

> `hasPriceChanges: true` sinaliza ao frontend exibir alerta de preço atualizado.
> O `subtotal` é calculado com `unitPrice` armazenado (preço aceito pelo usuário).

### Response (Sucesso — 200 OK, sem variações)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "status": "ACTIVE",
  "couponCode": null,
  "shippingMethod": "STANDARD",
  "subtotal": 299.85,
  "totalItems": 3,
  "hasPriceChanges": false,
  "items": [
    {
      "cartItemId": "01965f3a-0000-7000-0000-000000000060",
      "productId": "01965f3a-0000-7000-0000-000000000010",
      "productName": "Tênis Running Pro",
      "productStatus": "ACTIVE",
      "quantity": 3,
      "unitPrice": 99.95,
      "currentPrice": 99.95,
      "priceChanged": false,
      "subtotal": 299.85
    }
  ],
  "createdAt": "2026-04-11T14:00:00Z",
  "updatedAt": "2026-04-11T15:45:00Z",
  "expiresAt": "2026-04-12T14:00:00Z"
}
```

### Response (Erro — 404)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Carrinho não encontrado",
  "timestamp": "2026-04-11T15:50:00Z",
  "path": "/api/v1/cart"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`GetCartCommand`** — record com `userId` e `sessionId`.
2. **`GetCartOutput`** — record com nested `CartItemOutput` incluindo `unitPrice`, `currentPrice`, `priceChanged`. Factory `from(Cart, Map<UUID, Product>)`.
3. **`GetCartUseCase`** — lógica de enriquecimento com lookup de produtos; implementa `QueryUseCase` (sem `TransactionManager`).
4. **`@Bean getCartUseCase`** em `UseCaseConfig` — apenas `CartGateway` + `ProductGateway`.
5. **`GetCartResponse`** — record com factory `from(GetCartOutput)`.
6. **Endpoint `GET /`** no `CartController`.
7. **Testes unitários** — `GetCartUseCaseTest` com Mockito (sem Spring):
    - carrinho com items e preços iguais → `hasPriceChanges = false`, `priceChanged = false` em todos os itens
    - carrinho com item cujo preço mudou → `hasPriceChanges = true`, `priceChanged = true` no item afetado
    - produto deletado do catálogo (gateway retorna `Optional.empty()`) → fallback gracioso, sem exception
    - produto com status diferente de `ACTIVE` → retorna `productStatus` real, sem exception
    - carrinho não encontrado → `NotFoundException` propagada (404)
    - sem `userId` e sem `sessionId` → `Left(notification)` com erro
    - carrinho vazio (sem itens) → retorna com `items = []`, `subtotal = 0`, `hasPriceChanges = false`
    - confirmar que `TransactionManager` **nunca é injetado nem chamado** neste UC
8. **Testes de integração** (`GetCartIT.java` em `infrastructure/`) — Testcontainers + PostgreSQL real:
    - Retorna carrinho com todos os campos corretos.
    - Produto com preço alterado no catálogo → `priceChanged = true` no item correspondente.
    - `@Transactional(readOnly = true)` nos gateways — verificar que nenhum `UPDATE` é emitido.

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-87 AddItemToCart** | Cria o carrinho retornado por este UC |
| **UC-88 UpdateCartItemQuantity** | Atualiza itens; este UC exibe o estado pós-atualização |
| **UC-89 RemoveItemFromCart** | Remove itens; este UC exibe o estado pós-remoção |
| **UC-100 PlaceOrder** | Chama este UC (ou lógica equivalente) para validar preços antes do checkout |

---

## 💡 Considerações de Design

### Subtotal com preço armazenado vs. preço atual
O `subtotal` **usa o `unitPrice` armazenado** — o preço que o usuário viu e aceitou ao adicionar o item. O `currentPrice` é informativo para alertar o usuário; a atualização efetiva do preço no carrinho deve ser explicitamente solicitada (via UC "RefreshCartPrices", se implementado, ou reimplementação pelo usuário).

### Estratégia anti-N+1
Com a implementação atual (`findById()` por item), um carrinho com 10 produtos distintos faz 10 queries ao catálogo. Para otimizar, adicionar ao `ProductGateway`:
```java
// ProductGateway — método a adicionar se necessário
List<Product> findAllByIds(List<ProductId> ids);
```
E usar `repository.findAllById(ids)` no `ProductPostgresGateway`, substituindo o loop de `findById()` individual.
