# Domain Layer Reference

> Module: `domain/` — NO Spring imports allowed.
> Depends on: `shared` only.

## Table of Contents
1. [AggregateRoot](#aggregateroot)
2. [Entity](#entity)
3. [Typed ID](#typed-id)
4. [Gateway Interface](#gateway-interface)
5. [Domain Event](#domain-event)
6. [Validator](#validator)

## AggregateRoot

```java
package com.ecommerce.domain.order;

import com.ecommerce.shared.domain.AggregateRoot;
import com.ecommerce.shared.enums.OrderStatus;
import com.ecommerce.shared.enums.CancellationReason;
import com.ecommerce.shared.enums.ShippingMethod;
import com.ecommerce.shared.enums.PaymentMethodType;
import com.ecommerce.shared.valueobject.Money;
import com.ecommerce.domain.order.event.OrderCreatedEvent;
import com.ecommerce.domain.order.event.OrderCancelledEvent;

import java.time.Instant;
import java.util.List;

public class Order extends AggregateRoot<OrderId> {

    private String orderNumber;
    private OrderStatus status;
    private Money subtotal;
    private Money discountAmount;
    private Money shippingAmount;
    private Money total;
    private ShippingMethod shippingMethod;
    private PaymentMethodType paymentMethod;
    private List<OrderItem> items;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    // Private constructor — NEVER public
    private Order(
        final OrderId id,
        final String orderNumber,
        final OrderStatus status,
        final Money subtotal,
        final Money discountAmount,
        final Money shippingAmount,
        final Money total,
        final List<OrderItem> items
    ) {
        super(id);
        this.orderNumber = orderNumber;
        this.status = status;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.shippingAmount = shippingAmount;
        this.total = total;
        this.items = items;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Factory method for creation
    public static Order create(
        final OrderId id,
        final String orderNumber,
        final Money subtotal,
        final Money discountAmount,
        final Money shippingAmount,
        final Money total,
        final List<OrderItem> items
    ) {
        final var order = new Order(
            id, orderNumber, OrderStatus.PENDING,
            subtotal, discountAmount, shippingAmount, total, items
        );
        order.registerEvent(new OrderCreatedEvent(order.getId()));
        return order;
    }

    // Factory method for reconstitution (from DB)
    public static Order with(
        final OrderId id,
        final String orderNumber,
        final OrderStatus status,
        /* ... all fields ... */
        final int version
    ) {
        final var order = new Order(id, orderNumber, status, /* ... */);
        order.version = version;
        return order;
    }

    // Business methods — mutate state + register events
    public void cancel(final CancellationReason reason) {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel order in status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
        registerEvent(new OrderCancelledEvent(this.getId(), reason));
    }

    // Getters (no setters)
    public OrderStatus getStatus() { return status; }
    public Money getTotal() { return total; }
    public int getVersion() { return version; }
}
```

### Rules for AggregateRoots

- Extend `AggregateRoot<ID>` from `shared`
- Constructor is **always private**
- `create(...)` — factory for new instances, registers creation event
- `with(...)` — factory for reconstitution from DB, no events
- Business methods validate invariants, mutate state, register events
- Only expose getters, never setters

## Entity

Same pattern as AggregateRoot but extends `Entity<ID>`. Used for non-root entities owned by an aggregate (e.g., `OrderItem`, `CartItem`, `ProductImage`).

## Typed ID

```java
package com.ecommerce.domain.order;

import com.ecommerce.shared.domain.Identifier;
import java.util.UUID;

public class OrderId extends Identifier {

    private OrderId(final UUID value) {
        super(value);
    }

    public static OrderId from(final UUID value) {
        return new OrderId(value);
    }

    public static OrderId unique() {
        return new OrderId(UUID.randomUUID());
    }
}
```

## Gateway Interface

```java
package com.ecommerce.domain.order;

import com.ecommerce.shared.pagination.SearchQuery;
import com.ecommerce.shared.pagination.Pagination;
import java.util.Optional;

public interface OrderGateway {

    Order create(Order order);
    Order update(Order order);
    Optional<Order> findById(OrderId id);
    Pagination<Order> findAll(SearchQuery query);
    void deleteById(OrderId id);
}
```

### Rules for Gateways

- Always an interface in the `domain/` module
- One per aggregate root (or significant entity that needs its own repository)
- Uses domain types only (never JPA entities)
- Implementation lives in `infrastructure/`

## Domain Event

```java
package com.ecommerce.domain.order.event;

import com.ecommerce.shared.domain.DomainEvent;
import com.ecommerce.domain.order.OrderId;
import java.time.Instant;

public record OrderCreatedEvent(
    OrderId orderId,
    Instant occurredOn
) implements DomainEvent {

    public OrderCreatedEvent(final OrderId orderId) {
        this(orderId, Instant.now());
    }
}
```

## Validator

```java
package com.ecommerce.domain.order;

import com.ecommerce.shared.validation.Error;
import com.ecommerce.shared.validation.ValidationHandler;
import com.ecommerce.shared.validation.Validator;

public class OrderValidator extends Validator<Order> {

    public OrderValidator(final Order order, final ValidationHandler handler) {
        super(order, handler);
    }

    @Override
    public void validate() {
        checkOrderItems();
        checkTotal();
    }

    private void checkOrderItems() {
        if (this.entity().getItems() == null || this.entity().getItems().isEmpty()) {
            this.validationHandler().append(new Error("'items' must not be empty"));
        }
    }

    private void checkTotal() {
        if (this.entity().getTotal().isNegative()) {
            this.validationHandler().append(new Error("'total' must not be negative"));
        }
    }
}
```