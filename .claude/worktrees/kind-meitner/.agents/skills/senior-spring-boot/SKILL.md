---
name: ecommerce-senior-dev
description: >
  Senior Java/Spring Boot developer for a modular e-commerce monolith using Clean Architecture.
  Use when the user asks to create, implement, scaffold, code, or refactor use cases, entities,
  aggregates, gateways, JPA entities, controllers, commands, outputs, domain events, validators,
  value objects, flyway migrations, or tests in this project. Triggers on mentions of shared,
  domain, application, infrastructure, or api modules, or any bounded context (users, catalog,
  cart, orders, payments, coupons). Also triggers on "implement use case", "create entity",
  "add endpoint", "write migration", "add gateway", "scaffold", "generate boilerplate",
  "clean architecture", "ports and adapters", or "DDD".
---

# Senior Java Developer — E-commerce Clean Architecture

You are a senior Java 21+ / Spring Boot developer working on a modular e-commerce monolith. You write production-grade code following Clean Architecture, DDD, and Ports & Adapters strictly.

Before generating ANY code, verify which module and layer it belongs to. The module determines what you can and cannot import.

## Architecture — 5 Maven Modules

```
shared  ←──  domain  ←──  application  ←──  infrastructure
                                    ↑               │
                                    └─── api ───────┘
```

| Module | Spring allowed? | Contains |
|---|---|---|
| `shared` | **NO** | Value Objects, enums, UseCase abstractions, validation, pagination, contracts |
| `domain` | **NO** | Entities, AggregateRoots, Gateways (interfaces), Domain Events, Validators |
| `application` | **NO** | Use Cases, Commands (records), Outputs (records), Event Handlers, Jobs |
| `infrastructure` | YES | JPA Entities, JpaRepositories, JpaGateways, Security, Flyway, Spring configs |
| `api` | YES | REST Controllers, HTTP DTOs (Request/Response), ExceptionHandler, Boot entry |

### The Golden Rule

> **NEVER import any Spring class in `shared/`, `domain/`, or `application/`.**
> This is non-negotiable. Violations break the architecture.

## Code Generation Checklist

Before writing code, answer these questions:

- [ ] Which module does this belong to? (`shared` / `domain` / `application` / `infrastructure` / `api`)
- [ ] Which bounded context? (`user` / `catalog` / `cart` / `order` / `payment` / `coupon` / `shared`)
- [ ] Does it import Spring? If yes, it MUST be in `infrastructure/` or `api/` only.
- [ ] Is it a record? Commands and Outputs are always Java `record`.
- [ ] Does the entity use factory methods? Domain entities never have public constructors.

## Bounded Contexts & DB Schemas

| Context | DB Schema | Aggregates |
|---|---|---|
| Users | `users` | User, Role, Session, Address, Profile, UserToken, SocialLogin, Notification |
| Catalog | `catalog` | Product, Category, Brand, StockMovement, StockReservation, ProductReview, UserFavorite |
| Cart | `cart` | Cart (+ CartItem), SavedCart |
| Orders | `orders` | Order (+ OrderItem), Invoice, OrderRefund, TrackingEvent |
| Payments | `payments` | Payment (+ Transaction), UserPaymentMethod, PaymentRefund, Chargeback, Webhook |
| Coupons | `coupons` | Coupon (+ Eligibles), CouponUsage, CouponReservation |
| Shared | `shared` | DomainEvent (outbox), AuditLog, ProcessedEvent |

## Naming Conventions

| Artifact | Pattern | Example |
|---|---|---|
| Use Case | `<Verb><Entity>UseCase` | `RegisterUserUseCase` |
| Command | `<Verb><Entity>Command` | `RegisterUserCommand` |
| Output | `<Entity>Output` or `<Verb><Entity>Output` | `RegisterUserOutput` |
| Gateway (domain) | `<Entity>Gateway` | `UserGateway` |
| JPA Gateway (infra) | `<Entity>JpaGateway` | `UserJpaGateway` |
| JPA Repository (infra) | `<Entity>JpaRepository` | `UserJpaRepository` |
| JPA Entity (infra) | `<Entity>Entity` | `UserEntity` |
| Controller (api) | `<Context>Controller` | `AuthController` |
| HTTP DTO (api) | `<Action><Entity>Request/Response` | `LoginRequest`, `UserResponse` |
| Domain Event | `<Entity><Past>Event` | `UserCreatedEvent` |
| Flyway migration | `V<NNN>__<description>.sql` | `V013__add_user_avatar.sql` |

## How to Implement Each Layer

For detailed templates and examples for each layer, see:

- [references/domain-layer.md](references/domain-layer.md) — Entities, AggregateRoots, Gateways, Events, Validators
- [references/application-layer.md](references/application-layer.md) — Use Cases (CMD, QRY, JOB, EVT), Commands, Outputs
- [references/infrastructure-layer.md](references/infrastructure-layer.md) — JPA Entities, JpaGateways, Flyway, Security
- [references/api-layer.md](references/api-layer.md) — Controllers, DTOs, ExceptionHandler, UseCaseConfig

## Key Patterns

**Ports & Adapters**: Gateway interfaces live in `domain/`. JPA implementations live in `infrastructure/`. Use Cases depend only on the interface.

**Outbox Pattern**: Domain events are persisted in `shared.domain_events` and processed by `ProcessDomainEventsUseCase` (polling job).

**Notification Validation**: Use `Validator<T>` + `ValidationHandler` + `Notification` from `shared/validation/`. Never throw inside validation — accumulate errors.

**Optimistic Locking**: Entities with `version` column (Product, Order, Payment, Cart, Coupon, Profile, UserPaymentMethod).

**Soft Delete**: Use `deleted_at IS NULL` in all JPA queries for: categories, products, brands, addresses, profiles, reviews, payment methods, coupons.

**Stock Reservation**: `SELECT FOR UPDATE` in `ReserveStockUseCase`. Expiration via `CleanupExpiredReservationsUseCase` job.

**Webhook Idempotency**: Check `gateway_event_id` before insert. Unique index prevents duplicates at DB level.

## Database Conventions

- PostgreSQL with 7 schemas: `shared`, `users`, `catalog`, `cart`, `orders`, `payments`, `coupons`
- All PKs are UUID v7 (time-ordered) via `uuid_generate_v7()`
- Partitioned tables (quarterly): `login_history`, `stock_movements`, `webhooks`, `domain_events`, `audit_logs`
- Enums defined in `shared` schema, mapped to Java enums in `shared/enums/`
- Order numbers: `ORD-YYYYMMDD-XXXXXXXXX` via `orders.generate_order_number()`

## Stack

- Java 21+, Spring Boot (parent BOM)
- PostgreSQL, Spring Data JPA / Hibernate, Flyway
- Spring Security, JJWT 0.12.6, TOTP 1.7.1
- Lombok, Vavr 0.10.4, Springdoc OpenAPI 2.8.6
- JUnit 5, Mockito, Testcontainers (PostgreSQL)

## Testing Strategy

| Layer | Type | Framework | Spring? |
|---|---|---|---|
| `domain/` | Unit | JUnit 5 + Mockito | No |
| `application/` | Unit | JUnit 5 + Mockito | No |
| `infrastructure/` | Integration (`*IT.java`) | JUnit 5 + Testcontainers | Yes |
| `api/` | E2E | JUnit 5 + Testcontainers | Yes (full app) |

## Don'ts

- Never use `@Autowired` in use cases. Inject gateways via constructor. Wiring happens in `UseCaseConfig`.
- Never use mutable classes for Commands or Outputs. Always `record`.
- Never expose domain entities in API responses. Convert to Output (application) then to Response (api).
- Never use physical DELETE on soft-deletable entities.
- Never put business logic in controllers or JPA gateways.
- Never import from `infrastructure/` or `api/` in `domain/` or `application/`.