# CLAUDE.md

## Visão Geral do Projeto

E-commerce monolítico modular em Java 21+ com Spring Boot, seguindo Clean Architecture. O projeto é dividido em 5 módulos Maven com fronteiras de dependência rígidas.

## Arquitetura de Módulos

```
shared  ←──  domain  ←──  application  ←──  infrastructure
                                    ↑               │
                                    │               │
                                    └─── api ───────┘
```

| Módulo | Spring? | Função |
|---|---|---|
| `shared` | **Não** | Kernel: Value Objects, enums, abstrações de use case, validação, paginação, contratos |
| `domain` | **Não** | Entidades, Aggregates, Gateways (interfaces/portas), Domain Events, Validators |
| `application` | **Não** | Use Cases (171 total: 105 CMD, 46 QRY, 2 EVT, 11 JOB), Commands, Outputs |
| `infrastructure` | Sim | JPA Entities/Repositories/Gateways, Security (JWT, TOTP), Flyway, configs Spring |
| `api` | Sim | REST Controllers, DTOs HTTP (Request/Response), `@SpringBootApplication`, Exception Handlers |

**Regra inviolável**: `shared`, `domain` e `application` **nunca** importam Spring. Zero dependências de framework nesses módulos.

## Stack Tecnológica

- **Java 21+**, Spring Boot (starter-parent)
- **Persistência**: PostgreSQL, Spring Data JPA / Hibernate, Flyway
- **Segurança**: Spring Security, JJWT 0.12.6, TOTP (dev.samstevens.totp 1.7.1)
- **Utilitários**: Lombok, Vavr 0.10.4, Springdoc OpenAPI 2.8.6
- **Testes**: JUnit 5, Mockito, Testcontainers (PostgreSQL)

## Banco de Dados

PostgreSQL com 7 schemas: `shared`, `users`, `catalog`, `cart`, `orders`, `payments`, `coupons`.

Características importantes:
- UUIDs v7 (time-ordered) via função `uuid_generate_v7()` — usar em todas as PKs
- Tabelas particionadas por trimestre (RANGE on `created_at`): `login_history`, `stock_movements`, `webhooks`, `domain_events`, `audit_logs`
- Enums PostgreSQL no schema `shared` — mapeados como Java enums no módulo `shared/enums/`
- Soft delete via `deleted_at` (categories, products, brands, addresses, profiles, reviews, payment methods, coupons)
- Optimistic locking via coluna `version` (products, profiles, carts, orders, payments, coupons, user_methods)
- Função `orders.generate_order_number()` para número de pedido formato `ORD-YYYYMMDD-XXXXXXXXX`

## Convenções de Código

### Nomenclatura
- Use Cases: `<Verbo><Entidade>UseCase` (ex: `RegisterUserUseCase`, `PlaceOrderUseCase`)
- Commands: `<Verbo><Entidade>Command` — Java records imutáveis
- Outputs: `<Entidade>Output` ou `<Verbo><Entidade>Output` — Java records
- Gateways (domain): `<Entidade>Gateway` — interface/porta de saída
- JPA Gateway (infra): `<Entidade>JpaGateway` — implementa o Gateway do domain
- JPA Repository (infra): `<Entidade>JpaRepository` — extends JpaRepository do Spring Data
- JPA Entity (infra): `<Entidade>Entity` — mapeamento @Entity
- Controllers (api): `<Contexto>Controller` — anotados com @RestController
- DTOs HTTP (api): `<Ação><Entidade>Request` / `<Entidade>Response`

### Estrutura de um Use Case (application)
```
application/<contexto>/<ação>/
├── <Verbo><Entidade>UseCase.java      extends UseCase<Command, Output>
├── <Verbo><Entidade>Command.java      record imutável (entrada)
└── <Verbo><Entidade>Output.java       record imutável (saída)
```

Use Cases de escrita sem retorno estendem `UnitUseCase<Command>`. Queries estendem `QueryUseCase<Input, Output>`.

### Hierarquia de classes base (shared)
- `AggregateRoot<ID>` → `Entity<ID>` — com lista interna de `DomainEvent`
- `Identifier` — base para IDs tipados (ex: `UserId`, `OrderId`)
- `ValueObject` — base para VOs (ex: `Cpf`, `Email`, `Money`, `Slug`, `PostalCode`, `PhoneNumber`)
- `Validator<T>` + `ValidationHandler` + `Notification` — padrão Notification para validação
- `UseCase<I, O>`, `UnitUseCase<I>`, `QueryUseCase<I, O>` — contratos base

### Padrões aplicados
- **Ports & Adapters**: Gateways no `domain/`, implementações JPA no `infrastructure/`
- **Outbox Pattern**: Eventos persistidos em `shared.domain_events`, processados por polling (`ProcessDomainEventsUseCase`)
- **Idempotência de webhooks**: `gateway_event_id` + unique index em `payments.webhooks`
- **Reserva de estoque**: `SELECT FOR UPDATE` em `ReserveStockUseCase`, com expiração automática via job
- **Soft delete**: Usar `deleted_at IS NULL` em todos os queries; nunca DELETE físico nestas entidades

## Regras ao Gerar Código

1. **Nunca** adicionar imports Spring em `shared/`, `domain/` ou `application/`.
2. Commands e Outputs devem ser `record` (Java 16+). Nunca usar classes mutáveis.
3. Entidades de domínio usam factory methods (`create(...)`, `with(...)`) — nunca construtores públicos.
4. Toda mutação de estado no aggregate deve registrar um `DomainEvent` via `registerEvent()`.
5. JPA Entities (`infrastructure/`) são distintas das entidades de domínio (`domain/`). Converter via métodos `toDomain()` e `toEntity()` no JPA Gateway.
6. Use Cases recebem Gateways do domain via construtor (injeção manual, não `@Autowired`). O `UseCaseConfig` no `api/` faz o wiring.
7. Controllers no `api/` convertem Request → Command, invocam o Use Case, convertem Output → Response.
8. Flyway migrations seguem `V<NNN>__<descricao>.sql` em `infrastructure/src/main/resources/db/migration/`.
9. Testes unitários no `domain/` e `application/` usam apenas JUnit 5 + Mockito (sem Spring).
10. Testes de integração (`*IT.java`) no `infrastructure/` usam Testcontainers com PostgreSQL.
11. Testes E2E no `api/` sobem a aplicação completa com Testcontainers.

## Contextos de Negócio (Bounded Contexts)

| Contexto | Schema DB | Aggregates principais |
|---|---|---|
| **Users** | `users` | User, Role, Session, Address, Profile, UserToken, SocialLogin, Notification |
| **Catalog** | `catalog` | Product, Category, Brand, StockMovement, StockReservation, ProductReview, UserFavorite |
| **Cart** | `cart` | Cart (com CartItem), SavedCart |
| **Orders** | `orders` | Order (com OrderItem), Invoice, OrderRefund, TrackingEvent |
| **Payments** | `payments` | Payment (com Transaction), UserPaymentMethod, PaymentRefund, Chargeback, Webhook |
| **Coupons** | `coupons` | Coupon (com Eligibles), CouponUsage, CouponReservation |
| **Shared** | `shared` | DomainEvent (outbox), AuditLog, ProcessedEvent |

## Fluxos Críticos

### Checkout (Orders #100-102 + Payments #128-130)
`InitiateCheckout` → valida carrinho + reserva estoque → `SelectShippingMethod` → `PlaceOrder` → cria pedido PENDING + marca cart CONVERTED → `CreatePayment` → `AuthorizePayment` → `CapturePayment` → `ConfirmOrder` (PENDING → CONFIRMED)

### Cancelamento (Orders #107)
`CancelOrder` → muda status + registra motivo → libera estoque reservado (`ReleaseStock`) → reverte cupom se usado (`RevertCouponUsage`) → aciona void/refund do pagamento

### Webhooks (Payments #146-147)
`ReceiveWebhook` → grava cru com idempotência via `gateway_event_id` → `ProcessPendingWebhooks` (job) → despacha para `HandlePaymentWebhook` → atualiza Payment + Order conforme evento

### Cupom no Carrinho (Coupons #156-159)
`ValidateCoupon` → verifica elegibilidade → `ApplyCouponToCart` → cria reserva → no checkout `ConfirmCouponUsage` → registra uso + incrementa `current_uses`

## Jobs Agendados (11 total)

Definidos em `api/config/ScheduledJobsConfig.java` via `@Scheduled`, delegando para use cases em `application/**/job/`:

- `CleanupExpiredTokens`, `CleanupExpiredSessions` (Users)
- `CleanupExpiredReservations` (Catalog/Stock)
- `ExpireAbandonedCarts` (Cart)
- `ProcessPendingWebhooks`, `CancelExpiredPayments` (Payments)
- `ExpireCoupons`, `CleanupExpiredCouponReservations`, `ExpireDepletedCoupons` (Coupons)
- `ProcessDomainEvents`, `RetryFailedEvents` (Shared/Events)