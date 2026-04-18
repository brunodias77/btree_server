# API Layer Reference

> Module: `api/` — Spring IS allowed. Entry point of the application.
> Depends on: `shared`, `domain`, `application`, `infrastructure`.

## Table of Contents

1. [Controller](#controller)
2. [HTTP DTOs](#http-dtos)
3. [UseCaseConfig](#usecaseconfig)
4. [ScheduledJobsConfig](#scheduledjobsconfig)
5. [GlobalExceptionHandler](#globalexceptionhandler)

## Controller

```java
package com.ecommerce.api.order;

import com.ecommerce.api.order.dto.*;
import com.ecommerce.application.order.checkout.*;
import com.ecommerce.application.order.retrieve.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ListUserOrdersUseCase listUserOrdersUseCase;

    public OrderController(
        final PlaceOrderUseCase placeOrderUseCase,
        final GetOrderUseCase getOrderUseCase,
        final ListUserOrdersUseCase listUserOrdersUseCase
    ) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.listUserOrdersUseCase = listUserOrdersUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        // 1. Convert Request → Command
        final var command = request.toCommand();
        // 2. Execute use case
        final var output = placeOrderUseCase.execute(command);
        // 3. Convert Output → Response
        return ResponseEntity
            .created(URI.create("/api/v1/orders/" + output.orderId()))
            .body(OrderResponse.from(output));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
        final var output = getOrderUseCase.execute(id);
        return ResponseEntity.ok(OrderResponse.from(output));
    }
}
```

### Rules

- Controllers ONLY do: parse request → build command → call use case → build response
- Zero business logic in controllers
- Use `@Valid` for Bean Validation on requests
- Return `ResponseEntity<T>` with proper HTTP status codes

## HTTP DTOs

### Request (input)

```java
package com.ecommerce.api.order.dto;

import com.ecommerce.application.order.checkout.PlaceOrderCommand;
import jakarta.validation.constraints.NotBlank;

public record PlaceOrderRequest(
    @NotBlank String cartId,
    @NotBlank String shippingAddressId,
    @NotBlank String shippingMethod,
    @NotBlank String paymentMethodType
) {
    public PlaceOrderCommand toCommand(/* userId from SecurityContext */) {
        return new PlaceOrderCommand(cartId, /* userId */, shippingAddressId, shippingMethod, paymentMethodType);
    }
}
```

### Response (output)

```java
package com.ecommerce.api.order.dto;

import com.ecommerce.application.order.checkout.PlaceOrderOutput;

public record OrderResponse(
    String id,
    String orderNumber,
    String status,
    String total
) {
    public static OrderResponse from(final PlaceOrderOutput output) {
        return new OrderResponse(output.orderId(), output.orderNumber(), output.status(), output.total());
    }
}
```

### Rules

- Request DTOs use Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Size`, etc.)
- Response DTOs convert from application Output — never from domain entities
- Request has `toCommand()`, Response has `static from(Output)`

## UseCaseConfig

Wires use cases manually (they are not Spring beans themselves):

```java
package com.ecommerce.api.config;

import com.ecommerce.application.order.checkout.PlaceOrderUseCase;
import com.ecommerce.domain.order.OrderGateway;
import com.ecommerce.domain.cart.CartGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public PlaceOrderUseCase placeOrderUseCase(
        final OrderGateway orderGateway,
        final CartGateway cartGateway,
        final StockGateway stockGateway
    ) {
        return new PlaceOrderUseCase(orderGateway, cartGateway, stockGateway);
    }

    // ... one @Bean per use case
}
```

## ScheduledJobsConfig

```java
package com.ecommerce.api.config;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableScheduling
public class ScheduledJobsConfig {

    private final ProcessPendingWebhooksUseCase processPendingWebhooks;
    private final ExpireAbandonedCartsUseCase expireAbandonedCarts;
    // ... other jobs

    @Scheduled(fixedDelay = 30_000)
    public void processPendingWebhooks() {
        processPendingWebhooks.execute(null);
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void expireAbandonedCarts() {
        expireAbandonedCarts.execute(null);
    }
}
```

## GlobalExceptionHandler

```java
package com.ecommerce.api.exception;

import com.ecommerce.shared.exception.*;
import com.ecommerce.shared.validation.ValidationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(final NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(final ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(final ValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiError.of(ex.getErrors()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(final BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(ex.getMessage()));
    }
}
```
