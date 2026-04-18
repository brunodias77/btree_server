# Infrastructure Layer Reference

> Module: `infrastructure/` — Spring IS allowed.
> Depends on: `shared`, `domain`, `application`.

## Table of Contents
1. [JPA Entity](#jpa-entity)
2. [JPA Repository](#jpa-repository)
3. [JPA Gateway](#jpa-gateway)
4. [Flyway Migration](#flyway-migration)
5. [Integration Tests](#integration-tests)

## JPA Entity

JPA Entities are **distinct** from domain entities. They are data-mapping objects only.

```java
package com.ecommerce.infrastructure.order.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "orders")
@Getter @Setter @NoArgsConstructor
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private String status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "shipping_amount", nullable = false)
    private BigDecimal shippingAmount;

    @Column(nullable = false)
    private BigDecimal total;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Conversion methods
    public static OrderEntity toEntity(final Order order) {
        final var entity = new OrderEntity();
        entity.setId(order.getId().getValue());
        entity.setOrderNumber(order.getOrderNumber());
        entity.setStatus(order.getStatus().name());
        entity.setSubtotal(order.getSubtotal().amount());
        entity.setDiscountAmount(order.getDiscountAmount().amount());
        entity.setShippingAmount(order.getShippingAmount().amount());
        entity.setTotal(order.getTotal().amount());
        entity.setVersion(order.getVersion());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());
        return entity;
    }

    public Order toDomain() {
        return Order.with(
            OrderId.from(this.id),
            this.orderNumber,
            OrderStatus.valueOf(this.status),
            Money.of(this.subtotal),
            Money.of(this.discountAmount),
            Money.of(this.shippingAmount),
            Money.of(this.total),
            /* items loaded separately */
            this.version
        );
    }
}
```

### Rules
- Annotate with `@Entity`, `@Table(name, schema)`
- Use `@Version` for optimistic locking where applicable
- Soft-deletable entities: add `@Where(clause = "deleted_at IS NULL")` or filter in repository
- `toEntity(Domain)` and `toDomain()` handle conversion — never leak JPA entities outside infra

## JPA Repository

```java
package com.ecommerce.infrastructure.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserId(UUID userId);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status")
    List<OrderEntity> findByStatus(String status);
}
```

## JPA Gateway

Implements the domain Gateway interface. This is the Adapter in Ports & Adapters.

```java
package com.ecommerce.infrastructure.order.persistence;

import com.ecommerce.domain.order.*;
import com.ecommerce.shared.pagination.*;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;

@Component
public class OrderJpaGateway implements OrderGateway {

    private final OrderJpaRepository repository;

    public OrderJpaGateway(final OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order create(final Order order) {
        final var entity = OrderEntity.toEntity(order);
        return repository.save(entity).toDomain();
    }

    @Override
    public Order update(final Order order) {
        final var entity = OrderEntity.toEntity(order);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(final OrderId id) {
        return repository.findById(id.getValue()).map(OrderEntity::toDomain);
    }

    @Override
    public Pagination<Order> findAll(final SearchQuery query) {
        final var page = repository.findAll(
            PageRequest.of(query.page(), query.perPage())
        );
        return new Pagination<>(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.map(OrderEntity::toDomain).toList()
        );
    }
}
```

### Rules
- Annotate with `@Component`
- Constructor-inject the JpaRepository
- Always convert JPA Entity ↔ Domain Entity at the gateway boundary
- Never return JPA entities to the caller

## Flyway Migration

File: `infrastructure/src/main/resources/db/migration/V013__add_tracking_url_to_orders.sql`

```sql
ALTER TABLE orders.orders
    ADD COLUMN tracking_url TEXT;

COMMENT ON COLUMN orders.orders.tracking_url IS 'URL for shipment tracking';
```

### Naming rules
- `V<NNN>__<snake_case_description>.sql`
- Always specify the schema (`orders.orders`, not just `orders`)
- Use sequential numbering — check the last migration number before creating

## Integration Tests

```java
package com.ecommerce.infrastructure.order.persistence;

import com.ecommerce.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class OrderJpaGatewayIT extends IntegrationTestBase {

    @Autowired
    private OrderJpaGateway gateway;

    @Test
    void shouldCreateAndRetrieveOrder() {
        final var order = Order.create(/* ... */);
        final var saved = gateway.create(order);

        final var found = gateway.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrderNumber()).isEqualTo(order.getOrderNumber());
    }
}
```

`IntegrationTestBase` uses `@Testcontainers` with PostgreSQL and `@SpringBootTest`.