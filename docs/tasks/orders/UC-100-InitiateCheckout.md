# Task: UC-100 — InitiateCheckout

## 📋 Resumo

Inicia o processo de checkout validando o estado do carrinho, verificando disponibilidade de estoque para todos os itens, reservando o estoque (UC-71 internamente para cada item) e calculando os totais do pedido (subtotal, frete, desconto, total). Não cria o pedido — apenas prepara e valida tudo para que o usuário prossiga para seleção de método de pagamento.

Este UC é o **pré-checkout**: transforma um carrinho em uma sessão de checkout com estoque garantido e preços confirmados.

## 🎯 Objetivo

Ao final da implementação, o endpoint `POST /api/v1/checkout/initiate` deve:

1. Carregar o carrinho ativo do usuário (não-vazio obrigatório).
2. Carregar o endereço de entrega selecionado (`addressId`).
3. Para cada item do carrinho:
   - Verificar que o produto está `ACTIVE`.
   - Verificar que o produto tem estoque disponível (`quantity - reservasAtivas ≥ qtd do item`).
   - Reservar o estoque via `ReserveStockUseCase` (UC-71).
4. Calcular totais: `subtotal`, `shippingCost` (baseado no `shippingMethod`), `discount` (cupom ativo), `total`.
5. Retornar `200 OK` com o resumo do checkout (itens confirmados, reservas, totais, endereço snapshot) — pronto para o usuário confirmar o pagamento.

> **Importante:** o pedido (`orders.orders`) ainda **não é criado** neste UC. A criação ocorre em **UC-102 PlaceOrder**, após pagamento autorizado.

## 📦 Contexto Técnico

* **Módulo Principal:** `application`
* **Prioridade:** `CRÍTICO (P0)`
* **Endpoint:** `POST /api/v1/checkout/initiate`
* **Tabelas do Banco:**
  - `cart.carts`, `cart.cart_items` — leitura do carrinho ativo
  - `catalog.products` — leitura de preço e status
  - `catalog.stock_reservations` — criação de reservas (via UC-71)
  - `catalog.stock_movements` — ledger de reserva (via UC-71)
  - `users.addresses` — leitura do endereço de entrega

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

> Todos já existem.

1. **[VERIFICAR]** `domain/.../cart/gateway/CartGateway.java` — confirmar `findActiveByUserId()`.
2. **[VERIFICAR]** `domain/.../catalog/gateway/ProductGateway.java` — confirmar `findById()`.
3. **[VERIFICAR]** `domain/.../catalog/gateway/StockReservationGateway.java` — confirmar `sumActiveQuantityByProduct()`.
4. **[VERIFICAR]** `domain/.../order/valueobject/ShippingAddress.java` — confirmar `ShippingAddress.of()`.
5. **[VERIFICAR]** `domain/.../user/gateway/AddressGateway.java` — confirmar `findById()`.
6. **[VERIFICAR]** `domain/.../coupon/gateway/CouponGateway.java` — confirmar `findByCode()` para validar cupom aplicado ao carrinho.

### `application`

1. **[CRIAR]** `application/.../usecase/checkout/InitiateCheckoutCommand.java`
2. **[CRIAR]** `application/.../usecase/checkout/InitiateCheckoutOutput.java`
3. **[CRIAR]** `application/.../usecase/checkout/InitiateCheckoutUseCase.java`
4. **[CRIAR]** `application/.../usecase/checkout/ShippingCostCalculator.java` — interface para calcular frete

### `infrastructure`

> Verificar que os gateways do módulo `order` existem; se não, criar.

1. **[CRIAR SE AUSENTE]** `infrastructure/.../order/persistence/OrderJpaEntity.java`
2. **[CRIAR SE AUSENTE]** `infrastructure/.../order/persistence/OrderPostgresGateway.java`
3. **[CRIAR SE AUSENTE]** `infrastructure/.../checkout/service/FixedShippingCostCalculator.java` — implementação MVP do calculador de frete

### `api`

1. **[CRIAR]** `api/.../checkout/InitiateCheckoutRequest.java`
2. **[CRIAR]** `api/.../checkout/InitiateCheckoutResponse.java`
3. **[CRIAR]** `api/.../checkout/CheckoutController.java`
4. **[ALTERAR]** `api/.../config/UseCaseConfig.java` — adicionar `@Bean initiateCheckoutUseCase`.

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Command e Output (Application)

**`InitiateCheckoutCommand`**:
```java
package com.btree.application.usecase.checkout;

/**
 * Entrada para UC-100 — InitiateCheckout.
 *
 * @param userId         UUID do usuário autenticado
 * @param addressId      UUID do endereço de entrega selecionado
 * @param shippingMethod Método de envio escolhido (STANDARD, EXPRESS, SAME_DAY)
 */
public record InitiateCheckoutCommand(
        String userId,
        String addressId,
        String shippingMethod
) {}
```

**`InitiateCheckoutOutput`**:
```java
package com.btree.application.usecase.checkout;

import com.btree.domain.order.valueobject.ShippingAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Saída para UC-100 — InitiateCheckout.
 * Resumo do checkout pronto para exibição e confirmação de pagamento.
 */
public record InitiateCheckoutOutput(
        String cartId,
        List<CheckoutItemOutput> items,
        ShippingAddressOutput shippingAddress,
        String shippingMethod,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal discount,
        BigDecimal total,
        String couponCode,
        List<String> reservationIds,   // IDs das stock_reservations criadas
        Instant reservationsExpiresAt  // TTL das reservas (ex: 15 min)
) {
    public record CheckoutItemOutput(
            String productId,
            String productName,
            String productSku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}

    public record ShippingAddressOutput(
            String recipientName,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode,
            String country
    ) {
        public static ShippingAddressOutput from(final ShippingAddress addr) {
            return new ShippingAddressOutput(
                    addr.getRecipientName(), addr.getStreet(), addr.getNumber(),
                    addr.getComplement(), addr.getNeighborhood(), addr.getCity(),
                    addr.getState(), addr.getPostalCode(), addr.getCountry()
            );
        }
    }
}
```

### 2. Interface de Cálculo de Frete (Application)

```java
package com.btree.application.usecase.checkout;

import com.btree.shared.enums.ShippingMethod;

import java.math.BigDecimal;

/**
 * Porta de cálculo de frete.
 * Implementação MVP: valor fixo por método.
 * Implementação futura: integração com API de frete (Correios, Jadlog, etc.).
 */
public interface ShippingCostCalculator {
    BigDecimal calculate(ShippingMethod method);
}
```

**Implementação MVP em `infrastructure/`**:
```java
package com.btree.infrastructure.checkout.service;

import com.btree.application.usecase.checkout.ShippingCostCalculator;
import com.btree.shared.enums.ShippingMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Calculador de frete com valores fixos para o MVP.
 * Substituir por integração real com API de frete em sprint posterior.
 */
@Component
public class FixedShippingCostCalculator implements ShippingCostCalculator {

    @Override
    public BigDecimal calculate(final ShippingMethod method) {
        return switch (method) {
            case STANDARD  -> new BigDecimal("15.90");
            case EXPRESS   -> new BigDecimal("29.90");
            case SAME_DAY  -> new BigDecimal("49.90");
            default        -> BigDecimal.ZERO;
        };
    }
}
```

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.checkout;

import com.btree.application.usecase.catalog.product.ReserveStockCommand;
import com.btree.application.usecase.catalog.product.ReserveStockUseCase;
import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.gateway.CartGateway;
import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockReservationGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.order.error.OrderError;
import com.btree.domain.order.valueobject.ShippingAddress;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.enums.ShippingMethod;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.vavr.API.Left;

/**
 * UC-100 — InitiateCheckout [CMD P0].
 *
 * <p>Valida o carrinho, reserva estoque para todos os itens e calcula os totais.
 * Não cria o pedido — apenas prepara o checkout.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Carregar carrinho ativo — {@code NotFoundException} se ausente ou vazio.</li>
 *   <li>Carregar e converter endereço de entrega em {@link ShippingAddress} snapshot.</li>
 *   <li>Para cada item: verificar produto ACTIVE e estoque suficiente.</li>
 *   <li>Se tudo válido: reservar estoque via {@code ReserveStockUseCase} (UC-71).</li>
 *   <li>Calcular totais via {@link ShippingCostCalculator} e desconto de cupom.</li>
 *   <li>Retornar {@link InitiateCheckoutOutput} com reservas e totais.</li>
 * </ol>
 *
 * <p><b>Atomicidade de reservas:</b> As reservas são criadas uma a uma (loop).
 * Se alguma falhar, as já criadas devem ser liberadas (compensação cumulativa).
 * Ver tratamento no bloco try-catch abaixo.
 */
public class InitiateCheckoutUseCase implements UseCase<InitiateCheckoutCommand, InitiateCheckoutOutput> {

    private static final int RESERVATION_TTL_MINUTES = 15;

    private final CartGateway              cartGateway;
    private final ProductGateway           productGateway;
    private final StockReservationGateway  reservationGateway;
    private final AddressGateway           addressGateway;
    private final ReserveStockUseCase      reserveStockUseCase;
    private final ShippingCostCalculator   shippingCalculator;

    public InitiateCheckoutUseCase(
            final CartGateway cartGateway,
            final ProductGateway productGateway,
            final StockReservationGateway reservationGateway,
            final AddressGateway addressGateway,
            final ReserveStockUseCase reserveStockUseCase,
            final ShippingCostCalculator shippingCalculator
    ) {
        this.cartGateway        = cartGateway;
        this.productGateway     = productGateway;
        this.reservationGateway = reservationGateway;
        this.addressGateway     = addressGateway;
        this.reserveStockUseCase = reserveStockUseCase;
        this.shippingCalculator = shippingCalculator;
    }

    @Override
    public Either<Notification, InitiateCheckoutOutput> execute(final InitiateCheckoutCommand command) {

        // 1. Validar entrada
        final var notification = Notification.create();
        final UUID userId;
        final UUID addressId;
        final ShippingMethod shippingMethod;
        try {
            userId    = UUID.fromString(command.userId());
            addressId = UUID.fromString(command.addressId());
            shippingMethod = ShippingMethod.valueOf(command.shippingMethod().toUpperCase());
        } catch (final Exception e) {
            notification.append(new com.btree.shared.validation.Error(
                    "Parâmetros de entrada inválidos: " + e.getMessage()));
            return Left(notification);
        }

        // 2. Carregar carrinho ativo
        final var cart = cartGateway.findActiveByUserId(userId)
                .orElseThrow(() -> NotFoundException.with(CartError.CART_NOT_FOUND));

        if (cart.isEmpty()) {
            notification.append(CartError.CART_EMPTY);
            return Left(notification);
        }

        // 3. Carregar endereço de entrega
        final var address = addressGateway.findById(AddressId.from(addressId))
                .orElseThrow(() -> NotFoundException.with(
                        new com.btree.shared.validation.Error("Endereço de entrega não encontrado")));

        if (address.isDeleted()) {
            notification.append(new com.btree.shared.validation.Error("Endereço de entrega indisponível"));
            return Left(notification);
        }

        // 4. Validar disponibilidade de cada produto ANTES de reservar
        for (final var item : cart.getItems()) {
            final var product = productGateway.findById(ProductId.from(item.getProductId()))
                    .orElse(null);

            if (product == null || product.isDeleted()) {
                notification.append(new com.btree.shared.validation.Error(
                        "Produto não encontrado: " + item.getProductId()));
                continue;
            }
            if (!product.getStatus().name().equals("ACTIVE")) {
                notification.append(new com.btree.shared.validation.Error(
                        "Produto indisponível: " + product.getName()));
                continue;
            }
            // Verificar estoque líquido (produto.quantity − reservasAtivas)
            final int activeReserved = reservationGateway.sumActiveQuantityByProduct(
                    ProductId.from(item.getProductId()));
            final int available = product.getQuantity() - activeReserved;
            if (available < item.getQuantity()) {
                notification.append(new com.btree.shared.validation.Error(
                        "Estoque insuficiente para: " + product.getName() +
                        " (disponível: " + available + ", solicitado: " + item.getQuantity() + ")"
                ));
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 5. Reservar estoque para cada item (compensação se alguma falhar)
        final var expiresAt = Instant.now().plus(RESERVATION_TTL_MINUTES, ChronoUnit.MINUTES);
        final var reservationIds = new ArrayList<String>();

        try {
            for (final var item : cart.getItems()) {
                final var reserveCommand = new ReserveStockCommand(
                        item.getProductId().toString(),
                        item.getQuantity(),
                        null, // orderId ainda não existe
                        expiresAt
                );
                final var result = reserveStockUseCase.execute(reserveCommand);
                if (result.isLeft()) {
                    // Propagar erro de reserva e compensar reservas já criadas
                    releaseReservations(reservationIds);
                    return Left(result.getLeft());
                }
                reservationIds.add(result.get().reservationId());
            }
        } catch (final Exception e) {
            releaseReservations(reservationIds);
            notification.append(new com.btree.shared.validation.Error(
                    "Falha ao reservar estoque: " + e.getMessage()));
            return Left(notification);
        }

        // 6. Calcular totais
        final var subtotal     = cart.subtotal();
        final var shippingCost = shippingCalculator.calculate(shippingMethod);
        // Desconto por cupom — implementar quando CouponUseCase estiver disponível
        final var discount     = BigDecimal.ZERO;
        final var total        = subtotal.add(shippingCost).subtract(discount);

        // 7. Montar snapshot do endereço
        final var shippingAddressSnapshot = ShippingAddress.of(
                address.getFullName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode()
        );

        // 8. Construir output
        final var checkoutItems = cart.getItems().stream()
                .map(item -> {
                    final var product = productGateway.findById(
                            ProductId.from(item.getProductId())).orElseThrow();
                    return new InitiateCheckoutOutput.CheckoutItemOutput(
                            item.getProductId().toString(),
                            product.getName(),
                            product.getSku(),
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.subtotal()
                    );
                })
                .toList();

        final var output = new InitiateCheckoutOutput(
                cart.getId().getValue().toString(),
                checkoutItems,
                InitiateCheckoutOutput.ShippingAddressOutput.from(shippingAddressSnapshot),
                shippingMethod.name(),
                subtotal,
                shippingCost,
                discount,
                total,
                cart.getCouponCode(),
                reservationIds,
                expiresAt
        );

        return io.vavr.API.Right(output);
    }

    /**
     * Libera reservas já criadas em caso de falha parcial (compensação).
     * Chamado quando a reserva de algum item falha após outras já terem sido criadas.
     */
    private void releaseReservations(final List<String> reservationIds) {
        // TODO: chamar ReleaseStockUseCase (UC-72) para cada reservationId
        // Implementar quando UC-72 estiver disponível
        // for (final var id : reservationIds) {
        //     releaseStockUseCase.execute(new ReleaseStockCommand(id));
        // }
    }
}
```

### 4. Roteamento e Injeção (API)

**`InitiateCheckoutRequest`**:
```java
package com.btree.api.checkout;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body para POST /api/v1/checkout/initiate.
 */
public record InitiateCheckoutRequest(

        @NotBlank(message = "'addressId' é obrigatório")
        String addressId,

        @NotBlank(message = "'shippingMethod' é obrigatório")
        String shippingMethod    // "STANDARD" | "EXPRESS" | "SAME_DAY"
) {}
```

**`InitiateCheckoutResponse`**:
```java
package com.btree.api.checkout;

import com.btree.application.usecase.checkout.InitiateCheckoutOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InitiateCheckoutResponse(
        String cartId,
        List<CheckoutItemResponse> items,
        ShippingAddressResponse shippingAddress,
        String shippingMethod,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal discount,
        BigDecimal total,
        String couponCode,
        List<String> reservationIds,
        Instant reservationsExpiresAt
) {
    public record CheckoutItemResponse(
            String productId, String productName, String productSku,
            int quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record ShippingAddressResponse(
            String recipientName, String street, String number,
            String complement, String neighborhood, String city,
            String state, String postalCode, String country
    ) {}

    public static InitiateCheckoutResponse from(final InitiateCheckoutOutput output) {
        return new InitiateCheckoutResponse(
                output.cartId(),
                output.items().stream()
                        .map(i -> new CheckoutItemResponse(
                                i.productId(), i.productName(), i.productSku(),
                                i.quantity(), i.unitPrice(), i.subtotal()))
                        .toList(),
                new ShippingAddressResponse(
                        output.shippingAddress().recipientName(),
                        output.shippingAddress().street(),
                        output.shippingAddress().number(),
                        output.shippingAddress().complement(),
                        output.shippingAddress().neighborhood(),
                        output.shippingAddress().city(),
                        output.shippingAddress().state(),
                        output.shippingAddress().postalCode(),
                        output.shippingAddress().country()),
                output.shippingMethod(),
                output.subtotal(),
                output.shippingCost(),
                output.discount(),
                output.total(),
                output.couponCode(),
                output.reservationIds(),
                output.reservationsExpiresAt()
        );
    }
}
```

**`CheckoutController`**:
```java
@RestController
@RequestMapping("/api/v1/checkout")
@Tag(name = "Checkout", description = "Fluxo de checkout")
public class CheckoutController {

    private final InitiateCheckoutUseCase initiateCheckoutUseCase;

    public CheckoutController(final InitiateCheckoutUseCase initiateCheckoutUseCase) {
        this.initiateCheckoutUseCase = initiateCheckoutUseCase;
    }

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Iniciar checkout",
            description = "Valida o carrinho, reserva estoque para todos os itens e calcula totais. " +
                          "Não cria o pedido — apenas prepara o checkout. " +
                          "As reservas expiram em 15 minutos se o pedido não for finalizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkout iniciado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Carrinho ou endereço não encontrado"),
            @ApiResponse(responseCode = "422", description = "Carrinho vazio, produto inativo ou estoque insuficiente")
    })
    public InitiateCheckoutResponse initiateCheckout(
            @RequestHeader("X-User-Id") final String userId,
            @Valid @RequestBody final InitiateCheckoutRequest request
    ) {
        final var command = new InitiateCheckoutCommand(
                userId,
                request.addressId(),
                request.shippingMethod()
        );
        return InitiateCheckoutResponse.from(
                initiateCheckoutUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

**`UseCaseConfig.java`** — adicionar bean:
```java
@Bean
public InitiateCheckoutUseCase initiateCheckoutUseCase(
        final CartGateway cartGateway,
        final ProductGateway productGateway,
        final StockReservationGateway stockReservationGateway,
        final AddressGateway addressGateway,
        final ReserveStockUseCase reserveStockUseCase,
        final ShippingCostCalculator shippingCostCalculator
) {
    return new InitiateCheckoutUseCase(
            cartGateway,
            productGateway,
            stockReservationGateway,
            addressGateway,
            reserveStockUseCase,
            shippingCostCalculator
    );
}
```

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Constante | Condição | Status HTTP |
|---|---|---|---|
| Carrinho não encontrado | `NotFoundException` | Sem carrinho ativo | `404 Not Found` |
| Carrinho vazio | `CartError.CART_EMPTY` | `cart.isEmpty()` | `422 Unprocessable Entity` |
| Endereço não encontrado | `NotFoundException` | `addressGateway.findById()` vazio | `404 Not Found` |
| Endereço deletado | erro inline | `address.isDeleted()` | `422 Unprocessable Entity` |
| Produto não encontrado/deletado | erro inline | por item | `422 Unprocessable Entity` |
| Produto inativo | erro inline | `status != ACTIVE` | `422 Unprocessable Entity` |
| Estoque insuficiente | erro inline | `available < qty` | `422 Unprocessable Entity` |
| Falha na reserva (UC-71) | propagado do UC-71 | `reserveStockUseCase` retorna `Left` | `422 Unprocessable Entity` |
| UUID/enum inválido | erro inline | parse falha | `422 Unprocessable Entity` |

> **Compensação de reservas:** se itens A, B e C são reservados mas C falha, as reservas de A e B são liberadas via UC-72 (ReleaseStockUseCase) no bloco de compensação. O usuário recebe o erro de estoque insuficiente para C com uma mensagem clara.

---

## 🌐 Contrato da API REST

### Request — `POST /api/v1/checkout/initiate`

```json
{
  "addressId": "01965f3a-0000-7000-0000-000000000020",
  "shippingMethod": "STANDARD"
}
```

**Header obrigatório:** `X-User-Id: {userId}`

### Response (Sucesso — 200 OK)

```json
{
  "cartId": "01965f3a-0000-7000-0000-000000000050",
  "items": [
    {
      "productId": "01965f3a-0000-7000-0000-000000000010",
      "productName": "Tênis Running Pro",
      "productSku": "RUN-PRO-42",
      "quantity": 2,
      "unitPrice": 99.95,
      "subtotal": 199.90
    }
  ],
  "shippingAddress": {
    "recipientName": "João Silva",
    "street": "Rua das Flores",
    "number": "123",
    "complement": "Apto 4B",
    "neighborhood": "Jardim Europa",
    "city": "São Paulo",
    "state": "SP",
    "postalCode": "01310-000",
    "country": "BR"
  },
  "shippingMethod": "STANDARD",
  "subtotal": 199.90,
  "shippingCost": 15.90,
  "discount": 0.00,
  "total": 215.80,
  "couponCode": null,
  "reservationIds": [
    "01965f3a-0000-7000-0000-000000000060"
  ],
  "reservationsExpiresAt": "2026-04-11T16:15:00Z"
}
```

### Response (Erro — 422, estoque insuficiente)
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "errors": [
    "Estoque insuficiente para: Tênis Running Pro (disponível: 1, solicitado: 2)"
  ],
  "timestamp": "2026-04-11T16:00:00Z",
  "path": "/api/v1/checkout/initiate"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **Verificar `Address.getFullName()`** — confirmar que a entidade `user.Address` tem o campo ou combinação de nome para `recipientName`.
2. **`ShippingCostCalculator`** — interface em `application/`.
3. **`FixedShippingCostCalculator`** — implementação MVP em `infrastructure/` com `@Component`.
4. **`InitiateCheckoutCommand`** — record com 3 campos.
5. **`InitiateCheckoutOutput`** — record com nested `CheckoutItemOutput` e `ShippingAddressOutput`.
6. **`InitiateCheckoutUseCase`** — lógica completa (validação → reserva → totais → output).
7. **Completar `releaseReservations()`** — implementar o loop de chamada ao UC-72 (`ReleaseStockUseCase`).
8. **`@Bean initiateCheckoutUseCase`** em `UseCaseConfig`.
9. **`InitiateCheckoutRequest`** — record com `@NotBlank`.
10. **`InitiateCheckoutResponse`** — record com factory `from(InitiateCheckoutOutput)`.
11. **`CheckoutController`** — criar controller com endpoint único.
12. **Testes unitários** — `InitiateCheckoutUseCaseTest` com Mockito (sem Spring):
    - carrinho válido, estoque disponível → todas as reservas criadas, output com totais corretos
    - carrinho vazio → `Left(notification)` com `CART_EMPTY`
    - um produto inativo → `Left(notification)` com erro, **nenhuma reserva criada**
    - estoque insuficiente em item do meio → reservas anteriores liberadas (compensação), `Left(notification)`
    - endereço não encontrado → `NotFoundException` (404)
    - endereço deletado → `Left(notification)` com erro de endereço
    - falha no UC-71 para item B → reservas anteriores (item A) liberadas via UC-72
    - cálculo correto de `total = subtotal + shippingCost - discount`
13. **Testes de integração** (`InitiateCheckoutIT.java`) — Testcontainers + PostgreSQL real:
    - `catalog.stock_reservations` tem N linhas após iniciar checkout com N itens.
    - Todas as reservas têm `expires_at` dentro de ~15 min do now().
    - Falha forçada no terceiro item → as duas primeiras reservas são liberadas (rollback com compensação).

---

## 🔗 Relacionamento com outros UCs

| UC | Relação |
|---|---|
| **UC-71 ReserveStock** | Chamado internamente para reservar cada item |
| **UC-72 ReleaseStock** | Chamado para compensação se reserva parcial falhar |
| **UC-90 GetCart** | Deve ser chamado antes de InitiateCheckout para verificar preços |
| **UC-101 SelectShippingMethod** | Pode recalcular `shippingCost` sem re-reservar o estoque |
| **UC-102 PlaceOrder** | Próximo passo: cria o pedido usando o `cartId` e `reservationIds` deste UC |

### Fluxo completo de Checkout

```
GetCart (UC-90)
    │ verificar preços / hasPriceChanges
    ▼
InitiateCheckout (UC-100) ← este UC
    │ validar + reservar estoque + calcular totais
    ▼
[Pagamento autorizado]
    │
    ▼
ConfirmStockDeduction (UC-73)
    │
    ▼
PlaceOrder (UC-102)
    │ criar orders.orders com status PENDING
    ▼
ConfirmOrder (UC-106)
    │ PENDING → CONFIRMED
```
