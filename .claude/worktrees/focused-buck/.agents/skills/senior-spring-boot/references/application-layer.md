# Application Layer Reference

> Module: `application/` — NO Spring imports allowed.
> Depends on: `shared`, `domain`.

## Table of Contents
1. [Command Use Case (CMD)](#command-use-case)
2. [Query Use Case (QRY)](#query-use-case)
3. [Unit Use Case (no output)](#unit-use-case)
4. [Job Use Case (JOB)](#job-use-case)
5. [Event Handler (EVT)](#event-handler)
6. [File Organization](#file-organization)

## Command Use Case

```java
package com.ecommerce.application.order.checkout;

import com.ecommerce.shared.usecase.UseCase;

public class PlaceOrderUseCase extends UseCase<PlaceOrderCommand, PlaceOrderOutput> {

    private final OrderGateway orderGateway;
    private final CartGateway cartGateway;
    private final StockGateway stockGateway;

    // Constructor injection — no @Autowired, no Spring
    public PlaceOrderUseCase(
        final OrderGateway orderGateway,
        final CartGateway cartGateway,
        final StockGateway stockGateway
    ) {
        this.orderGateway = orderGateway;
        this.cartGateway = cartGateway;
        this.stockGateway = stockGateway;
    }

    @Override
    public PlaceOrderOutput execute(final PlaceOrderCommand cmd) {
        // 1. Validate
        // 2. Load aggregate(s) from gateways
        // 3. Call business methods on aggregate
        // 4. Persist via gateway
        // 5. Return output

        final var cart = cartGateway.findById(CartId.from(cmd.cartId()))
            .orElseThrow(() -> new NotFoundException("Cart", cmd.cartId()));

        final var order = Order.create(/* ... from cart ... */);
        final var saved = orderGateway.create(order);

        cart.markAsConverted();
        cartGateway.update(cart);

        return PlaceOrderOutput.from(saved);
    }
}
```

### Command (Input)

```java
package com.ecommerce.application.order.checkout;

// Always a record — immutable
public record PlaceOrderCommand(
    String cartId,
    String userId,
    String shippingAddressId,
    String shippingMethod,
    String paymentMethodType
) {}
```

### Output

```java
package com.ecommerce.application.order.checkout;

import com.ecommerce.domain.order.Order;

// Always a record — immutable
public record PlaceOrderOutput(
    String orderId,
    String orderNumber,
    String status,
    String total
) {
    public static PlaceOrderOutput from(final Order order) {
        return new PlaceOrderOutput(
            order.getId().getValue().toString(),
            order.getOrderNumber(),
            order.getStatus().name(),
            order.getTotal().toString()
        );
    }
}
```

## Query Use Case

```java
package com.ecommerce.application.order.retrieve;

import com.ecommerce.shared.usecase.QueryUseCase;

public class GetOrderUseCase extends QueryUseCase<String, OrderOutput> {

    private final OrderGateway orderGateway;

    public GetOrderUseCase(final OrderGateway orderGateway) {
        this.orderGateway = orderGateway;
    }

    @Override
    public OrderOutput execute(final String orderId) {
        return orderGateway.findById(OrderId.from(UUID.fromString(orderId)))
            .map(OrderOutput::from)
            .orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
```

## Unit Use Case

For commands that return nothing (void):

```java
package com.ecommerce.application.order.status;

import com.ecommerce.shared.usecase.UnitUseCase;

public class CancelOrderUseCase extends UnitUseCase<CancelOrderCommand> {

    private final OrderGateway orderGateway;

    public CancelOrderUseCase(final OrderGateway orderGateway) {
        this.orderGateway = orderGateway;
    }

    @Override
    public void execute(final CancelOrderCommand cmd) {
        final var order = orderGateway.findById(OrderId.from(UUID.fromString(cmd.orderId())))
            .orElseThrow(() -> new NotFoundException("Order", cmd.orderId()));

        order.cancel(CancellationReason.valueOf(cmd.reason()));
        orderGateway.update(order);
    }
}
```

## Job Use Case

Jobs live in `application/<context>/job/`. They are `UnitUseCase<Void>` or no-arg callables.

```java
package com.ecommerce.application.payment.webhook.job;

import com.ecommerce.shared.usecase.UnitUseCase;

public class ProcessPendingWebhooksUseCase extends UnitUseCase<Void> {

    private final WebhookGateway webhookGateway;

    public ProcessPendingWebhooksUseCase(final WebhookGateway webhookGateway) {
        this.webhookGateway = webhookGateway;
    }

    @Override
    public void execute(final Void unused) {
        final var pending = webhookGateway.findUnprocessed();
        pending.forEach(this::processWebhook);
    }

    private void processWebhook(final Webhook webhook) {
        // dispatch based on event_type, mark as processed
    }
}
```

Scheduled in `api/config/ScheduledJobsConfig.java`:

```java
@Scheduled(fixedDelay = 30_000)
public void processPendingWebhooks() {
    processPendingWebhooksUseCase.execute(null);
}
```

## Event Handler

```java
package com.ecommerce.application.cart.event;

import com.ecommerce.shared.event.EventHandler;
import com.ecommerce.domain.catalog.product.event.ProductPriceChangedEvent;

public class DetectPriceChangeHandler implements EventHandler<ProductPriceChangedEvent> {

    private final CartGateway cartGateway;

    public DetectPriceChangeHandler(final CartGateway cartGateway) {
        this.cartGateway = cartGateway;
    }

    @Override
    public void handle(final ProductPriceChangedEvent event) {
        // update current_price on active cart items
    }
}
```

## File Organization

```
application/<context>/<action>/
├── <Verb><Entity>UseCase.java
├── <Verb><Entity>Command.java      (CMD only)
└── <Verb><Entity>Output.java       (CMD/QRY)

application/<context>/job/
└── <Verb><Entity>UseCase.java      (JOB)

application/<context>/event/
└── <Name>Handler.java              (EVT)
```