# Estrutura de Pastas — Projeto Modular (Clean Architecture)

> **Convenção de módulos Maven/Gradle:**
>
> | Módulo | Spring Boot? | Descrição |
> |---|---|---|
> | `shared` | **Não** | Kernel compartilhado — Value Objects, abstrações, contratos |
> | `domain` | **Não** | Entidades, Aggregates, Domain Services, Domain Events |
> | `application` | **Não** | Use Cases, DTOs, portas (interfaces) de entrada/saída |
> | `infrastructure` | **Sim** | Implementações JPA, Security, Gateways, Config Spring |
> | `api` | **Sim** | Controllers REST, Filtros, Exception Handlers, Swagger, Boot |

---

```
ecommerce/
│
├── pom.xml  (parent — BOM, dependencyManagement, módulos)
│
│
│ ══════════════════════════════════════════════════════════════
│  MÓDULO: shared  (SEM Spring — pure Java + Vavr)
│ ══════════════════════════════════════════════════════════════
│
├── shared/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ecommerce/shared/
│       │   │
│       │   ├── domain/
│       │   │   ├── AggregateRoot.java
│       │   │   ├── Entity.java
│       │   │   ├── Identifier.java
│       │   │   ├── ValueObject.java
│       │   │   ├── DomainEvent.java
│       │   │   └── DomainException.java
│       │   │
│       │   ├── event/
│       │   │   ├── DomainEventPublisher.java
│       │   │   ├── EventBus.java
│       │   │   ├── EventHandler.java
│       │   │   ├── InMemoryEventBus.java
│       │   │   ├── IntegrationEvent.java
│       │   │   ├── IntegrationEventHandler.java
│       │   │   └── IntegrationEventPublisher.java
│       │   │
│       │   ├── valueobject/
│       │   │   ├── Cpf.java
│       │   │   ├── Email.java
│       │   │   ├── Money.java
│       │   │   ├── PhoneNumber.java
│       │   │   ├── PostalCode.java
│       │   │   └── Slug.java
│       │   │
│       │   ├── enums/
│       │   │   ├── CancellationReason.java
│       │   │   ├── CardBrand.java
│       │   │   ├── CartStatus.java
│       │   │   ├── ChargebackStatus.java
│       │   │   ├── CouponScope.java
│       │   │   ├── CouponStatus.java
│       │   │   ├── CouponType.java
│       │   │   ├── OrderStatus.java
│       │   │   ├── PaymentMethodType.java
│       │   │   ├── PaymentStatus.java
│       │   │   ├── ProductStatus.java
│       │   │   ├── RefundStatus.java
│       │   │   ├── ShippingMethod.java
│       │   │   ├── StockMovementType.java
│       │   │   ├── TokenType.java
│       │   │   └── TransactionType.java
│       │   │
│       │   ├── validation/
│       │   │   ├── Error.java
│       │   │   ├── Notification.java
│       │   │   ├── ThrowsValidationHandler.java
│       │   │   ├── ValidationException.java
│       │   │   ├── ValidationHandler.java
│       │   │   └── Validator.java
│       │   │
│       │   ├── exception/
│       │   │   ├── BusinessRuleException.java
│       │   │   ├── ConflictException.java
│       │   │   └── NotFoundException.java
│       │   │
│       │   ├── pagination/
│       │   │   ├── PageRequest.java
│       │   │   ├── PageResponse.java
│       │   │   ├── Pagination.java
│       │   │   └── SearchQuery.java
│       │   │
│       │   ├── usecase/
│       │   │   ├── UseCase.java
│       │   │   ├── UnitUseCase.java
│       │   │   └── QueryUseCase.java
│       │   │
│       │   └── contract/
│       │       ├── PasswordHasher.java
│       │       ├── TokenProvider.java
│       │       ├── TransactionManager.java
│       │       └── UuidGenerator.java
│       │
│       └── test/java/com/ecommerce/shared/
│           ├── valueobject/
│           │   ├── CpfTest.java
│           │   ├── EmailTest.java
│           │   ├── MoneyTest.java
│           │   └── SlugTest.java
│           └── validation/
│               └── NotificationTest.java
│
│
│ ══════════════════════════════════════════════════════════════
│  MÓDULO: domain  (SEM Spring — depende apenas de shared)
│ ══════════════════════════════════════════════════════════════
│
├── domain/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ecommerce/domain/
│       │   │
│       │   ├── user/
│       │   │   ├── User.java                        (AggregateRoot)
│       │   │   ├── UserId.java
│       │   │   ├── Profile.java                     (Entity — 1:1 com User)
│       │   │   ├── Address.java                     (Entity)
│       │   │   ├── AddressId.java
│       │   │   ├── Role.java                        (Entity)
│       │   │   ├── RoleId.java
│       │   │   ├── UserAuthority.java               (Entity)
│       │   │   ├── Session.java                     (Entity)
│       │   │   ├── SessionId.java
│       │   │   ├── UserToken.java                   (Entity)
│       │   │   ├── UserTokenId.java
│       │   │   ├── SocialLogin.java                 (Entity)
│       │   │   ├── SocialLoginId.java
│       │   │   ├── LoginHistory.java                (Entity)
│       │   │   ├── UserNotification.java            (Entity)
│       │   │   ├── UserNotificationId.java
│       │   │   ├── NotificationPreference.java      (Entity — 1:1 com User)
│       │   │   ├── UserGateway.java
│       │   │   ├── RoleGateway.java
│       │   │   ├── AddressGateway.java
│       │   │   ├── SessionGateway.java
│       │   │   ├── UserTokenGateway.java
│       │   │   ├── SocialLoginGateway.java
│       │   │   ├── LoginHistoryGateway.java
│       │   │   ├── NotificationGateway.java
│       │   │   ├── NotificationPreferenceGateway.java
│       │   │   ├── UserValidator.java
│       │   │   └── event/
│       │   │       ├── UserCreatedEvent.java
│       │   │       ├── UserEmailVerifiedEvent.java
│       │   │       ├── UserLockedEvent.java
│       │   │       ├── UserUnlockedEvent.java
│       │   │       ├── PasswordChangedEvent.java
│       │   │       └── AccountDisabledEvent.java
│       │   │
│       │   ├── catalog/
│       │   │   ├── product/
│       │   │   │   ├── Product.java                 (AggregateRoot)
│       │   │   │   ├── ProductId.java
│       │   │   │   ├── ProductImage.java            (Entity)
│       │   │   │   ├── ProductImageId.java
│       │   │   │   ├── ProductGateway.java
│       │   │   │   ├── ProductValidator.java
│       │   │   │   └── event/
│       │   │   │       ├── ProductCreatedEvent.java
│       │   │   │       ├── ProductPublishedEvent.java
│       │   │   │       ├── ProductPausedEvent.java
│       │   │   │       ├── ProductArchivedEvent.java
│       │   │   │       └── LowStockEvent.java
│       │   │   │
│       │   │   ├── category/
│       │   │   │   ├── Category.java                (AggregateRoot)
│       │   │   │   ├── CategoryId.java
│       │   │   │   ├── CategoryGateway.java
│       │   │   │   └── CategoryValidator.java
│       │   │   │
│       │   │   ├── brand/
│       │   │   │   ├── Brand.java                   (AggregateRoot)
│       │   │   │   ├── BrandId.java
│       │   │   │   ├── BrandGateway.java
│       │   │   │   └── BrandValidator.java
│       │   │   │
│       │   │   ├── stock/
│       │   │   │   ├── StockMovement.java           (Entity)
│       │   │   │   ├── StockMovementId.java
│       │   │   │   ├── StockReservation.java        (Entity)
│       │   │   │   ├── StockReservationId.java
│       │   │   │   └── StockGateway.java
│       │   │   │
│       │   │   ├── review/
│       │   │   │   ├── ProductReview.java           (AggregateRoot)
│       │   │   │   ├── ProductReviewId.java
│       │   │   │   ├── ReviewGateway.java
│       │   │   │   └── ReviewValidator.java
│       │   │   │
│       │   │   └── favorite/
│       │   │       ├── UserFavorite.java             (Entity)
│       │   │       ├── UserFavoriteId.java
│       │   │       └── FavoriteGateway.java
│       │   │
│       │   ├── cart/
│       │   │   ├── Cart.java                        (AggregateRoot)
│       │   │   ├── CartId.java
│       │   │   ├── CartItem.java                    (Entity)
│       │   │   ├── CartItemId.java
│       │   │   ├── SavedCart.java                   (Entity)
│       │   │   ├── SavedCartId.java
│       │   │   ├── CartActivityLog.java             (Entity)
│       │   │   ├── CartGateway.java
│       │   │   ├── SavedCartGateway.java
│       │   │   ├── CartValidator.java
│       │   │   └── event/
│       │   │       ├── CartConvertedEvent.java
│       │   │       ├── CartAbandonedEvent.java
│       │   │       └── CartItemPriceChangedEvent.java
│       │   │
│       │   ├── order/
│       │   │   ├── Order.java                       (AggregateRoot)
│       │   │   ├── OrderId.java
│       │   │   ├── OrderItem.java                   (Entity)
│       │   │   ├── OrderItemId.java
│       │   │   ├── OrderStatusHistory.java          (Entity)
│       │   │   ├── TrackingEvent.java               (Entity)
│       │   │   ├── TrackingEventId.java
│       │   │   ├── Invoice.java                     (Entity)
│       │   │   ├── InvoiceId.java
│       │   │   ├── OrderRefund.java                 (Entity)
│       │   │   ├── OrderRefundId.java
│       │   │   ├── OrderGateway.java
│       │   │   ├── InvoiceGateway.java
│       │   │   ├── OrderRefundGateway.java
│       │   │   ├── TrackingEventGateway.java
│       │   │   ├── OrderValidator.java
│       │   │   └── event/
│       │   │       ├── OrderCreatedEvent.java
│       │   │       ├── OrderConfirmedEvent.java
│       │   │       ├── OrderProcessingEvent.java
│       │   │       ├── OrderShippedEvent.java
│       │   │       ├── OrderDeliveredEvent.java
│       │   │       ├── OrderCancelledEvent.java
│       │   │       └── OrderRefundedEvent.java
│       │   │
│       │   ├── payment/
│       │   │   ├── Payment.java                     (AggregateRoot)
│       │   │   ├── PaymentId.java
│       │   │   ├── PaymentTransaction.java          (Entity)
│       │   │   ├── PaymentTransactionId.java
│       │   │   ├── PaymentRefund.java               (Entity)
│       │   │   ├── PaymentRefundId.java
│       │   │   ├── Chargeback.java                  (Entity)
│       │   │   ├── ChargebackId.java
│       │   │   ├── UserPaymentMethod.java           (AggregateRoot)
│       │   │   ├── UserPaymentMethodId.java
│       │   │   ├── Webhook.java                     (Entity)
│       │   │   ├── WebhookId.java
│       │   │   ├── PaymentGateway.java              (porta — repositório)
│       │   │   ├── PaymentProcessorGateway.java     (porta — gateway externo)
│       │   │   ├── UserPaymentMethodGateway.java
│       │   │   ├── PaymentRefundGateway.java
│       │   │   ├── ChargebackGateway.java
│       │   │   ├── WebhookGateway.java
│       │   │   ├── PaymentValidator.java
│       │   │   └── event/
│       │   │       ├── PaymentCreatedEvent.java
│       │   │       ├── PaymentAuthorizedEvent.java
│       │   │       ├── PaymentCapturedEvent.java
│       │   │       ├── PaymentFailedEvent.java
│       │   │       ├── PaymentVoidedEvent.java
│       │   │       ├── PaymentRefundedEvent.java
│       │   │       └── ChargebackOpenedEvent.java
│       │   │
│       │   └── coupon/
│       │       ├── Coupon.java                      (AggregateRoot)
│       │       ├── CouponId.java
│       │       ├── CouponReservation.java           (Entity)
│       │       ├── CouponReservationId.java
│       │       ├── CouponUsage.java                 (Entity)
│       │       ├── CouponUsageId.java
│       │       ├── EligibleCategory.java            (Entity)
│       │       ├── EligibleProduct.java             (Entity)
│       │       ├── EligibleUser.java                (Entity)
│       │       ├── CouponGateway.java
│       │       ├── CouponUsageGateway.java
│       │       ├── CouponReservationGateway.java
│       │       ├── CouponValidator.java
│       │       └── event/
│       │           ├── CouponActivatedEvent.java
│       │           ├── CouponAppliedEvent.java
│       │           ├── CouponDepletedEvent.java
│       │           └── CouponExpiredEvent.java
│       │
│       └── test/java/com/ecommerce/domain/
│           ├── user/
│           │   ├── UserTest.java
│           │   ├── AddressTest.java
│           │   └── SessionTest.java
│           ├── catalog/
│           │   ├── product/
│           │   │   └── ProductTest.java
│           │   ├── category/
│           │   │   └── CategoryTest.java
│           │   └── stock/
│           │       └── StockReservationTest.java
│           ├── cart/
│           │   └── CartTest.java
│           ├── order/
│           │   └── OrderTest.java
│           ├── payment/
│           │   └── PaymentTest.java
│           └── coupon/
│               └── CouponTest.java
│
│
│ ══════════════════════════════════════════════════════════════
│  MÓDULO: application  (SEM Spring — depende de shared + domain)
│  171 use cases — organizados por contexto e ação
│ ══════════════════════════════════════════════════════════════
│
├── application/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ecommerce/application/
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  USERS (44 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── user/
│       │   │   │
│       │   │   ├── register/
│       │   │   │   ├── RegisterUserUseCase.java             (#1  CMD P0)
│       │   │   │   ├── RegisterUserCommand.java
│       │   │   │   └── RegisterUserOutput.java
│       │   │   │
│       │   │   ├── authenticate/
│       │   │   │   ├── AuthenticateUserUseCase.java         (#2  CMD P0)
│       │   │   │   ├── AuthenticateUserCommand.java
│       │   │   │   └── AuthenticateUserOutput.java
│       │   │   │
│       │   │   ├── refresh/
│       │   │   │   ├── RefreshSessionUseCase.java           (#3  CMD P0)
│       │   │   │   ├── RefreshSessionCommand.java
│       │   │   │   └── RefreshSessionOutput.java
│       │   │   │
│       │   │   ├── logout/
│       │   │   │   ├── LogoutUserUseCase.java               (#4  CMD P0)
│       │   │   │   └── LogoutAllSessionsUseCase.java        (#12 CMD P2)
│       │   │   │
│       │   │   ├── retrieve/
│       │   │   │   ├── GetCurrentUserUseCase.java           (#5  QRY P0)
│       │   │   │   └── GetCurrentUserOutput.java
│       │   │   │
│       │   │   ├── verify/
│       │   │   │   ├── VerifyEmailUseCase.java              (#6  CMD P1)
│       │   │   │   └── VerifyEmailCommand.java
│       │   │   │
│       │   │   ├── password/
│       │   │   │   ├── RequestPasswordResetUseCase.java     (#7  CMD P1)
│       │   │   │   ├── ResetPasswordUseCase.java            (#8  CMD P1)
│       │   │   │   ├── ResetPasswordCommand.java
│       │   │   │   ├── ChangePasswordUseCase.java           (#22 CMD P1)
│       │   │   │   └── ChangePasswordCommand.java
│       │   │   │
│       │   │   ├── social/
│       │   │   │   ├── LoginWithSocialProviderUseCase.java  (#9  CMD P2)
│       │   │   │   ├── LoginWithSocialProviderCommand.java
│       │   │   │   └── LoginWithSocialProviderOutput.java
│       │   │   │
│       │   │   ├── twofactor/
│       │   │   │   ├── EnableTwoFactorUseCase.java          (#10 CMD P2)
│       │   │   │   ├── EnableTwoFactorOutput.java
│       │   │   │   ├── VerifyTwoFactorUseCase.java          (#11 CMD P2)
│       │   │   │   └── VerifyTwoFactorCommand.java
│       │   │   │
│       │   │   ├── profile/
│       │   │   │   ├── UpdateProfileUseCase.java            (#15 CMD P0)
│       │   │   │   ├── UpdateProfileCommand.java
│       │   │   │   ├── GetProfileUseCase.java               (#16 QRY P0)
│       │   │   │   ├── ProfileOutput.java
│       │   │   │   ├── ChangeEmailUseCase.java              (#23 CMD P2)
│       │   │   │   ├── ChangeEmailCommand.java
│       │   │   │   ├── UpdatePhoneNumberUseCase.java        (#24 CMD P2)
│       │   │   │   └── UpdatePhoneNumberCommand.java
│       │   │   │
│       │   │   ├── address/
│       │   │   │   ├── AddAddressUseCase.java               (#17 CMD P0)
│       │   │   │   ├── AddAddressCommand.java
│       │   │   │   ├── UpdateAddressUseCase.java            (#18 CMD P0)
│       │   │   │   ├── UpdateAddressCommand.java
│       │   │   │   ├── DeleteAddressUseCase.java            (#19 CMD P0)
│       │   │   │   ├── SetDefaultAddressUseCase.java        (#20 CMD P0)
│       │   │   │   ├── ListAddressesUseCase.java            (#21 QRY P0)
│       │   │   │   └── AddressOutput.java
│       │   │   │
│       │   │   ├── role/
│       │   │   │   ├── AssignRoleUseCase.java               (#25 CMD P1)
│       │   │   │   ├── RevokeRoleUseCase.java               (#26 CMD P1)
│       │   │   │   ├── GrantAuthorityUseCase.java           (#27 CMD P1)
│       │   │   │   ├── RevokeAuthorityUseCase.java          (#28 CMD P1)
│       │   │   │   ├── ListRolesUseCase.java                (#29 QRY P1)
│       │   │   │   ├── RoleOutput.java
│       │   │   │   ├── CreateRoleUseCase.java               (#30 CMD P2)
│       │   │   │   ├── CreateRoleCommand.java
│       │   │   │   └── DeleteRoleUseCase.java               (#31 CMD P2)
│       │   │   │
│       │   │   ├── account/
│       │   │   │   ├── DisableAccountUseCase.java           (#32 CMD P2)
│       │   │   │   ├── LockAccountUseCase.java              (#33 CMD P3)
│       │   │   │   └── UnlockAccountUseCase.java            (#34 CMD P3)
│       │   │   │
│       │   │   ├── notification/
│       │   │   │   ├── SendNotificationUseCase.java         (#35 CMD P2)
│       │   │   │   ├── SendNotificationCommand.java
│       │   │   │   ├── ListUnreadNotificationsUseCase.java  (#36 QRY P2)
│       │   │   │   ├── NotificationOutput.java
│       │   │   │   ├── MarkNotificationAsReadUseCase.java   (#37 CMD P2)
│       │   │   │   ├── MarkAllNotificationsAsReadUseCase.java (#43 CMD P3)
│       │   │   │   ├── DeleteNotificationUseCase.java       (#44 CMD P3)
│       │   │   │   ├── UpdateNotificationPreferencesUseCase.java (#38 CMD P2)
│       │   │   │   ├── UpdateNotificationPreferencesCommand.java
│       │   │   │   ├── GetNotificationPreferencesUseCase.java   (#39 QRY P2)
│       │   │   │   └── NotificationPreferenceOutput.java
│       │   │   │
│       │   │   ├── session/
│       │   │   │   ├── RevokeSpecificSessionUseCase.java    (#40 CMD P2)
│       │   │   │   ├── GetLoginHistoryUseCase.java          (#41 QRY P3)
│       │   │   │   ├── LoginHistoryOutput.java
│       │   │   │   ├── ListActiveSessionsUseCase.java       (#42 QRY P3)
│       │   │   │   └── SessionOutput.java
│       │   │   │
│       │   │   └── job/
│       │   │       ├── CleanupExpiredTokensUseCase.java     (#13 JOB P3)
│       │   │       └── CleanupExpiredSessionsUseCase.java   (#14 JOB P3)
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  CATALOG (42 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── catalog/
│       │   │   │
│       │   │   ├── category/
│       │   │   │   ├── create/
│       │   │   │   │   ├── CreateCategoryUseCase.java       (#45 CMD P0)
│       │   │   │   │   └── CreateCategoryCommand.java
│       │   │   │   ├── update/
│       │   │   │   │   ├── UpdateCategoryUseCase.java       (#46 CMD P0)
│       │   │   │   │   ├── UpdateCategoryCommand.java
│       │   │   │   │   ├── ReorderCategoriesUseCase.java    (#49 CMD P1)
│       │   │   │   │   └── DeactivateCategoryUseCase.java   (#50 CMD P1)
│       │   │   │   └── retrieve/
│       │   │   │       ├── GetCategoryUseCase.java          (#47 QRY P0)
│       │   │   │       ├── CategoryOutput.java
│       │   │   │       ├── ListCategoriesUseCase.java       (#48 QRY P0)
│       │   │   │       └── GetCategoryBreadcrumbUseCase.java (#51 QRY P2)
│       │   │   │
│       │   │   ├── brand/
│       │   │   │   ├── create/
│       │   │   │   │   ├── CreateBrandUseCase.java          (#52 CMD P0)
│       │   │   │   │   └── CreateBrandCommand.java
│       │   │   │   ├── update/
│       │   │   │   │   ├── UpdateBrandUseCase.java          (#53 CMD P0)
│       │   │   │   │   ├── UpdateBrandCommand.java
│       │   │   │   │   └── DeactivateBrandUseCase.java      (#56 CMD P1)
│       │   │   │   └── retrieve/
│       │   │   │       ├── GetBrandUseCase.java             (#54 QRY P0)
│       │   │   │       ├── BrandOutput.java
│       │   │   │       └── ListBrandsUseCase.java           (#55 QRY P0)
│       │   │   │
│       │   │   ├── product/
│       │   │   │   ├── create/
│       │   │   │   │   ├── CreateProductUseCase.java        (#57 CMD P0)
│       │   │   │   │   ├── CreateProductCommand.java
│       │   │   │   │   └── CreateProductOutput.java
│       │   │   │   ├── update/
│       │   │   │   │   ├── UpdateProductUseCase.java        (#58 CMD P0)
│       │   │   │   │   └── UpdateProductCommand.java
│       │   │   │   ├── status/
│       │   │   │   │   ├── PublishProductUseCase.java       (#59 CMD P0)
│       │   │   │   │   ├── PauseProductUseCase.java         (#60 CMD P0)
│       │   │   │   │   ├── ArchiveProductUseCase.java       (#61 CMD P0)
│       │   │   │   │   └── RestoreProductUseCase.java       (#68 CMD P2)
│       │   │   │   ├── retrieve/
│       │   │   │   │   ├── GetProductUseCase.java           (#62 QRY P0)
│       │   │   │   │   ├── ProductOutput.java
│       │   │   │   │   ├── SearchProductsUseCase.java       (#63 QRY P0)
│       │   │   │   │   ├── ListProductsByCategoryUseCase.java (#64 QRY P0)
│       │   │   │   │   ├── ListFeaturedProductsUseCase.java (#66 QRY P1)
│       │   │   │   │   ├── ListProductsByBrandUseCase.java  (#67 QRY P1)
│       │   │   │   │   └── ListProductsByTagUseCase.java    (#69 QRY P2)
│       │   │   │   └── image/
│       │   │   │       ├── ManageProductImagesUseCase.java  (#65 CMD P1)
│       │   │   │       └── ManageProductImagesCommand.java
│       │   │   │
│       │   │   ├── stock/
│       │   │   │   ├── AdjustStockUseCase.java              (#70 CMD P0)
│       │   │   │   ├── AdjustStockCommand.java
│       │   │   │   ├── ReserveStockUseCase.java             (#71 CMD P0)
│       │   │   │   ├── ReserveStockCommand.java
│       │   │   │   ├── ReleaseStockUseCase.java             (#72 CMD P0)
│       │   │   │   ├── ConfirmStockDeductionUseCase.java    (#73 CMD P0)
│       │   │   │   ├── GetStockMovementsUseCase.java        (#74 QRY P1)
│       │   │   │   ├── StockMovementOutput.java
│       │   │   │   ├── ListLowStockProductsUseCase.java     (#75 QRY P1)
│       │   │   │   └── job/
│       │   │   │       └── CleanupExpiredReservationsUseCase.java (#76 JOB P2)
│       │   │   │
│       │   │   ├── review/
│       │   │   │   ├── SubmitProductReviewUseCase.java      (#77 CMD P2)
│       │   │   │   ├── SubmitProductReviewCommand.java
│       │   │   │   ├── ListProductReviewsUseCase.java       (#78 QRY P2)
│       │   │   │   ├── ReviewOutput.java
│       │   │   │   ├── ApproveReviewUseCase.java            (#79 CMD P2)
│       │   │   │   ├── RespondToReviewUseCase.java          (#80 CMD P2)
│       │   │   │   ├── RespondToReviewCommand.java
│       │   │   │   ├── DeleteReviewUseCase.java             (#84 CMD P2)
│       │   │   │   ├── ListUserReviewsUseCase.java          (#85 QRY P2)
│       │   │   │   └── GetProductReviewSummaryUseCase.java  (#86 QRY P2)
│       │   │   │
│       │   │   └── favorite/
│       │   │       ├── AddToFavoritesUseCase.java           (#81 CMD P2)
│       │   │       ├── RemoveFromFavoritesUseCase.java      (#82 CMD P2)
│       │   │       ├── ListFavoritesUseCase.java            (#83 QRY P2)
│       │   │       └── FavoriteOutput.java
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  CART (13 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── cart/
│       │   │   ├── additem/
│       │   │   │   ├── AddItemToCartUseCase.java            (#87 CMD P0)
│       │   │   │   └── AddItemToCartCommand.java
│       │   │   │
│       │   │   ├── updatequantity/
│       │   │   │   ├── UpdateCartItemQuantityUseCase.java   (#88 CMD P0)
│       │   │   │   └── UpdateCartItemQuantityCommand.java
│       │   │   │
│       │   │   ├── removeitem/
│       │   │   │   └── RemoveItemFromCartUseCase.java       (#89 CMD P0)
│       │   │   │
│       │   │   ├── retrieve/
│       │   │   │   ├── GetCartUseCase.java                  (#90 QRY P0)
│       │   │   │   ├── CartOutput.java
│       │   │   │   └── GetCartActivityLogUseCase.java       (#94 QRY P2)
│       │   │   │
│       │   │   ├── merge/
│       │   │   │   └── MergeGuestCartUseCase.java           (#91 CMD P1)
│       │   │   │
│       │   │   ├── clear/
│       │   │   │   └── ClearCartUseCase.java                (#92 CMD P1)
│       │   │   │
│       │   │   ├── savedcart/
│       │   │   │   ├── SaveCartForLaterUseCase.java         (#96 CMD P2)
│       │   │   │   ├── RestoreSavedCartUseCase.java         (#97 CMD P2)
│       │   │   │   ├── ListSavedCartsUseCase.java           (#98 QRY P2)
│       │   │   │   ├── SavedCartOutput.java
│       │   │   │   └── DeleteSavedCartUseCase.java          (#99 CMD P2)
│       │   │   │
│       │   │   ├── event/
│       │   │   │   └── DetectPriceChangeHandler.java        (#93 EVT P2)
│       │   │   │
│       │   │   └── job/
│       │   │       └── ExpireAbandonedCartsUseCase.java     (#95 JOB P1)
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  ORDERS (23 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── order/
│       │   │   ├── checkout/
│       │   │   │   ├── InitiateCheckoutUseCase.java         (#100 CMD P0)
│       │   │   │   ├── InitiateCheckoutCommand.java
│       │   │   │   ├── InitiateCheckoutOutput.java
│       │   │   │   ├── SelectShippingMethodUseCase.java     (#101 CMD P0)
│       │   │   │   ├── SelectShippingMethodCommand.java
│       │   │   │   ├── PlaceOrderUseCase.java               (#102 CMD P0)
│       │   │   │   ├── PlaceOrderCommand.java
│       │   │   │   ├── PlaceOrderOutput.java
│       │   │   │   ├── CalculateOrderTotalsUseCase.java     (#103 QRY P0)
│       │   │   │   └── OrderTotalsOutput.java
│       │   │   │
│       │   │   ├── retrieve/
│       │   │   │   ├── GetOrderUseCase.java                 (#104 QRY P0)
│       │   │   │   ├── OrderOutput.java
│       │   │   │   ├── ListUserOrdersUseCase.java           (#105 QRY P0)
│       │   │   │   ├── GetOrderStatusHistoryUseCase.java    (#111 QRY P1)
│       │   │   │   ├── StatusHistoryOutput.java
│       │   │   │   ├── ListOrdersByStatusUseCase.java       (#112 QRY P1)
│       │   │   │   ├── ListAllOrdersUseCase.java            (#113 QRY P1)
│       │   │   │   └── SearchOrdersUseCase.java             (#114 QRY P2)
│       │   │   │
│       │   │   ├── status/
│       │   │   │   ├── ConfirmOrderUseCase.java             (#106 CMD P0)
│       │   │   │   ├── CancelOrderUseCase.java              (#107 CMD P0)
│       │   │   │   ├── CancelOrderCommand.java
│       │   │   │   ├── ProcessOrderUseCase.java             (#108 CMD P1)
│       │   │   │   ├── ShipOrderUseCase.java                (#109 CMD P1)
│       │   │   │   ├── ShipOrderCommand.java
│       │   │   │   ├── DeliverOrderUseCase.java             (#110 CMD P1)
│       │   │   │   └── AddOrderInternalNotesUseCase.java    (#115 CMD P2)
│       │   │   │
│       │   │   ├── tracking/
│       │   │   │   ├── AddTrackingEventUseCase.java         (#116 CMD P1)
│       │   │   │   ├── AddTrackingEventCommand.java
│       │   │   │   ├── GetTrackingEventsUseCase.java        (#117 QRY P1)
│       │   │   │   └── TrackingEventOutput.java
│       │   │   │
│       │   │   ├── invoice/
│       │   │   │   ├── IssueInvoiceUseCase.java             (#118 CMD P1)
│       │   │   │   ├── IssueInvoiceCommand.java
│       │   │   │   ├── GetInvoiceUseCase.java               (#119 QRY P1)
│       │   │   │   └── InvoiceOutput.java
│       │   │   │
│       │   │   └── refund/
│       │   │       ├── RequestOrderRefundUseCase.java       (#120 CMD P1)
│       │   │       ├── RequestOrderRefundCommand.java
│       │   │       ├── ProcessOrderRefundUseCase.java       (#121 CMD P1)
│       │   │       ├── ProcessOrderRefundCommand.java
│       │   │       ├── ListOrderRefundsUseCase.java         (#122 QRY P1)
│       │   │       └── OrderRefundOutput.java
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  PAYMENTS (25 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── payment/
│       │   │   ├── method/
│       │   │   │   ├── SavePaymentMethodUseCase.java        (#123 CMD P1)
│       │   │   │   ├── SavePaymentMethodCommand.java
│       │   │   │   ├── DeletePaymentMethodUseCase.java      (#124 CMD P1)
│       │   │   │   ├── SetDefaultPaymentMethodUseCase.java  (#125 CMD P1)
│       │   │   │   ├── GetPaymentMethodUseCase.java         (#126 QRY P1)
│       │   │   │   ├── PaymentMethodOutput.java
│       │   │   │   └── ListPaymentMethodsUseCase.java       (#127 QRY P1)
│       │   │   │
│       │   │   ├── process/
│       │   │   │   ├── CreatePaymentUseCase.java            (#128 CMD P0)
│       │   │   │   ├── CreatePaymentCommand.java
│       │   │   │   ├── CreatePaymentOutput.java
│       │   │   │   ├── AuthorizePaymentUseCase.java         (#129 CMD P0)
│       │   │   │   ├── CapturePaymentUseCase.java           (#130 CMD P0)
│       │   │   │   ├── HandlePaymentFailureUseCase.java     (#131 CMD P0)
│       │   │   │   └── VoidPaymentUseCase.java              (#133 CMD P1)
│       │   │   │
│       │   │   ├── webhook/
│       │   │   │   ├── HandlePaymentWebhookUseCase.java     (#132 EVT P0)
│       │   │   │   ├── ReceiveWebhookUseCase.java           (#146 CMD P0)
│       │   │   │   ├── ReceiveWebhookCommand.java
│       │   │   │   └── job/
│       │   │   │       └── ProcessPendingWebhooksUseCase.java (#147 JOB P0)
│       │   │   │
│       │   │   ├── retrieve/
│       │   │   │   ├── GetPaymentUseCase.java               (#134 QRY P1)
│       │   │   │   ├── PaymentOutput.java
│       │   │   │   ├── GeneratePixQRCodeUseCase.java        (#135 QRY P2)
│       │   │   │   ├── GenerateBoletoUseCase.java           (#136 QRY P2)
│       │   │   │   ├── ListPaymentsUseCase.java             (#137 QRY P2)
│       │   │   │   ├── GetPaymentTransactionsUseCase.java   (#138 QRY P2)
│       │   │   │   └── TransactionOutput.java
│       │   │   │
│       │   │   ├── refund/
│       │   │   │   └── RefundPaymentUseCase.java            (#139 CMD P1)
│       │   │   │
│       │   │   ├── chargeback/
│       │   │   │   ├── HandleChargebackUseCase.java         (#140 CMD P1)
│       │   │   │   ├── SubmitChargebackEvidenceUseCase.java (#141 CMD P2)
│       │   │   │   ├── SubmitChargebackEvidenceCommand.java
│       │   │   │   ├── ResolveChargebackUseCase.java        (#145 CMD P2)
│       │   │   │   ├── ResolveChargebackCommand.java
│       │   │   │   ├── ListChargebacksUseCase.java          (#143 QRY P2)
│       │   │   │   ├── ChargebackOutput.java
│       │   │   │   └── GetChargebackUseCase.java            (#144 QRY P2)
│       │   │   │
│       │   │   └── job/
│       │   │       └── CancelExpiredPaymentsUseCase.java    (#142 JOB P2)
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  COUPONS (17 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   ├── coupon/
│       │   │   ├── create/
│       │   │   │   ├── CreateCouponUseCase.java             (#148 CMD P1)
│       │   │   │   ├── CreateCouponCommand.java
│       │   │   │   └── CreateCouponOutput.java
│       │   │   │
│       │   │   ├── update/
│       │   │   │   ├── UpdateCouponUseCase.java             (#149 CMD P1)
│       │   │   │   └── UpdateCouponCommand.java
│       │   │   │
│       │   │   ├── status/
│       │   │   │   ├── ActivateCouponUseCase.java           (#150 CMD P1)
│       │   │   │   ├── PauseCouponUseCase.java              (#151 CMD P1)
│       │   │   │   └── DeactivateCouponUseCase.java         (#155 CMD P1)
│       │   │   │
│       │   │   ├── eligibility/
│       │   │   │   ├── SetCouponEligibilityUseCase.java     (#152 CMD P1)
│       │   │   │   └── SetCouponEligibilityCommand.java
│       │   │   │
│       │   │   ├── retrieve/
│       │   │   │   ├── ListCouponsUseCase.java              (#153 QRY P1)
│       │   │   │   ├── CouponOutput.java
│       │   │   │   ├── GetCouponUseCase.java                (#154 QRY P1)
│       │   │   │   └── GetCouponUsageHistoryUseCase.java    (#162 QRY P2)
│       │   │   │
│       │   │   ├── apply/
│       │   │   │   ├── ApplyCouponToCartUseCase.java        (#156 CMD P1)
│       │   │   │   ├── ApplyCouponToCartCommand.java
│       │   │   │   ├── RemoveCouponFromCartUseCase.java     (#157 CMD P1)
│       │   │   │   ├── ValidateCouponUseCase.java           (#158 CMD P1)
│       │   │   │   ├── ValidateCouponCommand.java
│       │   │   │   ├── ConfirmCouponUsageUseCase.java       (#159 CMD P1)
│       │   │   │   └── RevertCouponUsageUseCase.java        (#161 CMD P1)
│       │   │   │
│       │   │   └── job/
│       │   │       ├── ExpireCouponsUseCase.java            (#160 JOB P1)
│       │   │       ├── CleanupExpiredCouponReservationsUseCase.java (#163 JOB P2)
│       │   │       └── ExpireDepletedCouponsUseCase.java    (#164 JOB P3)
│       │   │
│       │   │ ────────────────────────────────────────
│       │   │  SHARED / INFRA (7 use cases)
│       │   │ ────────────────────────────────────────
│       │   │
│       │   └── shared/
│       │       ├── event/
│       │       │   ├── PublishDomainEventUseCase.java       (#165 CMD P1)
│       │       │   └── job/
│       │       │       ├── ProcessDomainEventsUseCase.java  (#166 JOB P1)
│       │       │       ├── RetryFailedEventsUseCase.java    (#167 JOB P2)
│       │       │       └── ArchiveProcessedEventsUseCase.java (#168 JOB P3)
│       │       │
│       │       └── audit/
│       │           ├── LogAuditEventUseCase.java            (#169 CMD P2)
│       │           ├── LogAuditEventCommand.java
│       │           ├── GetEntityAuditTrailUseCase.java      (#170 QRY P2)
│       │           ├── GetUserAuditTrailUseCase.java        (#171 QRY P2)
│       │           └── AuditLogOutput.java
│       │
│       └── test/java/com/ecommerce/application/
│           ├── user/
│           │   ├── register/
│           │   │   └── RegisterUserUseCaseTest.java
│           │   ├── authenticate/
│           │   │   └── AuthenticateUserUseCaseTest.java
│           │   ├── password/
│           │   │   └── ResetPasswordUseCaseTest.java
│           │   ├── address/
│           │   │   └── AddAddressUseCaseTest.java
│           │   └── role/
│           │       └── AssignRoleUseCaseTest.java
│           ├── catalog/
│           │   ├── product/
│           │   │   ├── create/
│           │   │   │   └── CreateProductUseCaseTest.java
│           │   │   └── status/
│           │   │       └── PublishProductUseCaseTest.java
│           │   ├── stock/
│           │   │   ├── ReserveStockUseCaseTest.java
│           │   │   └── ReleaseStockUseCaseTest.java
│           │   └── review/
│           │       └── SubmitProductReviewUseCaseTest.java
│           ├── cart/
│           │   ├── additem/
│           │   │   └── AddItemToCartUseCaseTest.java
│           │   └── merge/
│           │       └── MergeGuestCartUseCaseTest.java
│           ├── order/
│           │   ├── checkout/
│           │   │   └── PlaceOrderUseCaseTest.java
│           │   ├── status/
│           │   │   ├── ConfirmOrderUseCaseTest.java
│           │   │   └── CancelOrderUseCaseTest.java
│           │   └── refund/
│           │       └── ProcessOrderRefundUseCaseTest.java
│           ├── payment/
│           │   ├── process/
│           │   │   ├── CreatePaymentUseCaseTest.java
│           │   │   └── CapturePaymentUseCaseTest.java
│           │   ├── webhook/
│           │   │   └── ReceiveWebhookUseCaseTest.java
│           │   └── refund/
│           │       └── RefundPaymentUseCaseTest.java
│           └── coupon/
│               ├── apply/
│               │   ├── ValidateCouponUseCaseTest.java
│               │   └── ApplyCouponToCartUseCaseTest.java
│               └── status/
│                   └── ActivateCouponUseCaseTest.java
│
│
│ ══════════════════════════════════════════════════════════════
│  MÓDULO: infrastructure  (COM Spring Boot)
│ ══════════════════════════════════════════════════════════════
│
├── infrastructure/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/ecommerce/infrastructure/
│       │   │   │
│       │   │   ├── config/
│       │   │   │   ├── JpaConfig.java
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── JwtConfig.java
│       │   │   │   ├── FlywayConfig.java
│       │   │   │   ├── OpenApiConfig.java
│       │   │   │   ├── ObjectMapperConfig.java
│       │   │   │   ├── EventConfig.java
│       │   │   │   ├── SchedulingConfig.java
│       │   │   │   └── WebMvcConfig.java
│       │   │   │
│       │   │   ├── shared/
│       │   │   │   ├── BcryptPasswordHasher.java
│       │   │   │   ├── JwtTokenProvider.java
│       │   │   │   ├── JpaTransactionManagerAdapter.java
│       │   │   │   ├── UuidV7Generator.java
│       │   │   │   ├── SpringDomainEventPublisher.java
│       │   │   │   ├── SpringIntegrationEventPublisher.java
│       │   │   │   └── persistence/
│       │   │   │       ├── DomainEventEntity.java
│       │   │   │       ├── DomainEventJpaRepository.java
│       │   │   │       ├── ProcessedEventEntity.java
│       │   │   │       ├── ProcessedEventJpaRepository.java
│       │   │   │       ├── AuditLogEntity.java
│       │   │   │       └── AuditLogJpaRepository.java
│       │   │   │
│       │   │   ├── user/
│       │   │   │   ├── persistence/
│       │   │   │   │   ├── UserEntity.java
│       │   │   │   │   ├── UserJpaRepository.java
│       │   │   │   │   ├── UserJpaGateway.java
│       │   │   │   │   ├── ProfileEntity.java
│       │   │   │   │   ├── RoleEntity.java
│       │   │   │   │   ├── RoleJpaRepository.java
│       │   │   │   │   ├── RoleJpaGateway.java
│       │   │   │   │   ├── UserAuthorityEntity.java
│       │   │   │   │   ├── AddressEntity.java
│       │   │   │   │   ├── AddressJpaRepository.java
│       │   │   │   │   ├── AddressJpaGateway.java
│       │   │   │   │   ├── SessionEntity.java
│       │   │   │   │   ├── SessionJpaRepository.java
│       │   │   │   │   ├── SessionJpaGateway.java
│       │   │   │   │   ├── UserTokenEntity.java
│       │   │   │   │   ├── UserTokenJpaRepository.java
│       │   │   │   │   ├── UserTokenJpaGateway.java
│       │   │   │   │   ├── SocialLoginEntity.java
│       │   │   │   │   ├── SocialLoginJpaRepository.java
│       │   │   │   │   ├── SocialLoginJpaGateway.java
│       │   │   │   │   ├── LoginHistoryEntity.java
│       │   │   │   │   ├── LoginHistoryJpaRepository.java
│       │   │   │   │   ├── LoginHistoryJpaGateway.java
│       │   │   │   │   ├── NotificationEntity.java
│       │   │   │   │   ├── NotificationJpaRepository.java
│       │   │   │   │   ├── NotificationJpaGateway.java
│       │   │   │   │   ├── NotificationPreferenceEntity.java
│       │   │   │   │   ├── NotificationPreferenceJpaRepository.java
│       │   │   │   │   └── NotificationPreferenceJpaGateway.java
│       │   │   │   └── security/
│       │   │   │       ├── JwtAuthenticationFilter.java
│       │   │   │       ├── CustomUserDetailsService.java
│       │   │   │       └── TotpService.java
│       │   │   │
│       │   │   ├── catalog/
│       │   │   │   └── persistence/
│       │   │   │       ├── ProductEntity.java
│       │   │   │       ├── ProductJpaRepository.java
│       │   │   │       ├── ProductJpaGateway.java
│       │   │   │       ├── ProductImageEntity.java
│       │   │   │       ├── CategoryEntity.java
│       │   │   │       ├── CategoryJpaRepository.java
│       │   │   │       ├── CategoryJpaGateway.java
│       │   │   │       ├── BrandEntity.java
│       │   │   │       ├── BrandJpaRepository.java
│       │   │   │       ├── BrandJpaGateway.java
│       │   │   │       ├── StockMovementEntity.java
│       │   │   │       ├── StockReservationEntity.java
│       │   │   │       ├── StockReservationJpaRepository.java
│       │   │   │       ├── StockJpaGateway.java
│       │   │   │       ├── ProductReviewEntity.java
│       │   │   │       ├── ProductReviewJpaRepository.java
│       │   │   │       ├── ReviewJpaGateway.java
│       │   │   │       ├── UserFavoriteEntity.java
│       │   │   │       ├── UserFavoriteJpaRepository.java
│       │   │   │       └── FavoriteJpaGateway.java
│       │   │   │
│       │   │   ├── cart/
│       │   │   │   └── persistence/
│       │   │   │       ├── CartEntity.java
│       │   │   │       ├── CartJpaRepository.java
│       │   │   │       ├── CartJpaGateway.java
│       │   │   │       ├── CartItemEntity.java
│       │   │   │       ├── CartActivityLogEntity.java
│       │   │   │       ├── SavedCartEntity.java
│       │   │   │       ├── SavedCartJpaRepository.java
│       │   │   │       └── SavedCartJpaGateway.java
│       │   │   │
│       │   │   ├── order/
│       │   │   │   └── persistence/
│       │   │   │       ├── OrderEntity.java
│       │   │   │       ├── OrderJpaRepository.java
│       │   │   │       ├── OrderJpaGateway.java
│       │   │   │       ├── OrderItemEntity.java
│       │   │   │       ├── OrderStatusHistoryEntity.java
│       │   │   │       ├── TrackingEventEntity.java
│       │   │   │       ├── TrackingEventJpaRepository.java
│       │   │   │       ├── TrackingEventJpaGateway.java
│       │   │   │       ├── InvoiceEntity.java
│       │   │   │       ├── InvoiceJpaRepository.java
│       │   │   │       ├── InvoiceJpaGateway.java
│       │   │   │       ├── OrderRefundEntity.java
│       │   │   │       ├── OrderRefundJpaRepository.java
│       │   │   │       └── OrderRefundJpaGateway.java
│       │   │   │
│       │   │   ├── payment/
│       │   │   │   ├── persistence/
│       │   │   │   │   ├── PaymentEntity.java
│       │   │   │   │   ├── PaymentJpaRepository.java
│       │   │   │   │   ├── PaymentJpaGateway.java
│       │   │   │   │   ├── PaymentTransactionEntity.java
│       │   │   │   │   ├── PaymentRefundEntity.java
│       │   │   │   │   ├── PaymentRefundJpaRepository.java
│       │   │   │   │   ├── PaymentRefundJpaGateway.java
│       │   │   │   │   ├── ChargebackEntity.java
│       │   │   │   │   ├── ChargebackJpaRepository.java
│       │   │   │   │   ├── ChargebackJpaGateway.java
│       │   │   │   │   ├── UserPaymentMethodEntity.java
│       │   │   │   │   ├── UserPaymentMethodJpaRepository.java
│       │   │   │   │   ├── UserPaymentMethodJpaGateway.java
│       │   │   │   │   ├── WebhookEntity.java
│       │   │   │   │   ├── WebhookJpaRepository.java
│       │   │   │   │   └── WebhookJpaGateway.java
│       │   │   │   └── gateway/
│       │   │   │       ├── PaymentProcessorGatewayImpl.java
│       │   │   │       └── StripePaymentAdapter.java
│       │   │   │
│       │   │   └── coupon/
│       │   │       └── persistence/
│       │   │           ├── CouponEntity.java
│       │   │           ├── CouponJpaRepository.java
│       │   │           ├── CouponJpaGateway.java
│       │   │           ├── CouponUsageEntity.java
│       │   │           ├── CouponUsageJpaRepository.java
│       │   │           ├── CouponUsageJpaGateway.java
│       │   │           ├── CouponReservationEntity.java
│       │   │           ├── CouponReservationJpaRepository.java
│       │   │           ├── CouponReservationJpaGateway.java
│       │   │           ├── EligibleCategoryEntity.java
│       │   │           ├── EligibleProductEntity.java
│       │   │           └── EligibleUserEntity.java
│       │   │
│       │   └── resources/
│       │       └── db/migration/
│       │           ├── V001__create_schemas_and_extensions.sql
│       │           ├── V002__create_functions.sql
│       │           ├── V003__create_shared_enums.sql
│       │           ├── V004__create_users_tables.sql
│       │           ├── V005__create_catalog_tables.sql
│       │           ├── V006__create_cart_tables.sql
│       │           ├── V007__create_orders_tables.sql
│       │           ├── V008__create_payments_tables.sql
│       │           ├── V009__create_coupons_tables.sql
│       │           ├── V010__create_shared_events_audit.sql
│       │           ├── V011__create_late_foreign_keys.sql
│       │           └── V012__create_partitions.sql
│       │
│       └── test/java/com/ecommerce/infrastructure/
│           ├── IntegrationTestBase.java             (Testcontainers + PostgreSQL)
│           ├── user/
│           │   └── persistence/
│           │       ├── UserJpaGatewayIT.java
│           │       ├── AddressJpaGatewayIT.java
│           │       └── SessionJpaGatewayIT.java
│           ├── catalog/
│           │   └── persistence/
│           │       ├── ProductJpaGatewayIT.java
│           │       ├── CategoryJpaGatewayIT.java
│           │       └── StockJpaGatewayIT.java
│           ├── cart/
│           │   └── persistence/
│           │       └── CartJpaGatewayIT.java
│           ├── order/
│           │   └── persistence/
│           │       ├── OrderJpaGatewayIT.java
│           │       └── InvoiceJpaGatewayIT.java
│           ├── payment/
│           │   └── persistence/
│           │       ├── PaymentJpaGatewayIT.java
│           │       └── WebhookJpaGatewayIT.java
│           └── coupon/
│               └── persistence/
│                   └── CouponJpaGatewayIT.java
│
│
│ ══════════════════════════════════════════════════════════════
│  MÓDULO: api  (COM Spring Boot — ponto de entrada)
│ ══════════════════════════════════════════════════════════════
│
└── api/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/ecommerce/api/
        │   │   │
        │   │   ├── Application.java                        (@SpringBootApplication)
        │   │   │
        │   │   ├── config/
        │   │   │   ├── UseCaseConfig.java                  (@Configuration — registra beans)
        │   │   │   └── ScheduledJobsConfig.java            (@Configuration — agenda JOBs)
        │   │   │
        │   │   ├── filter/
        │   │   │   └── RequestLoggingFilter.java
        │   │   │
        │   │   ├── exception/
        │   │   │   ├── GlobalExceptionHandler.java         (@RestControllerAdvice)
        │   │   │   └── ApiError.java
        │   │   │
        │   │   ├── user/
        │   │   │   ├── AuthController.java                 (#1-4, 9)
        │   │   │   ├── TwoFactorController.java            (#10-11)
        │   │   │   ├── UserController.java                 (#5, 22-24)
        │   │   │   ├── ProfileController.java              (#15-16)
        │   │   │   ├── AddressController.java              (#17-21)
        │   │   │   ├── PasswordController.java             (#6-8)
        │   │   │   ├── RoleController.java                 (#25-31)
        │   │   │   ├── AccountController.java              (#32-34)
        │   │   │   ├── SessionController.java              (#40, 42)
        │   │   │   ├── LoginHistoryController.java         (#41)
        │   │   │   ├── NotificationController.java         (#35-37, 43-44)
        │   │   │   ├── NotificationPreferenceController.java (#38-39)
        │   │   │   └── dto/
        │   │   │       ├── RegisterUserRequest.java
        │   │   │       ├── LoginRequest.java
        │   │   │       ├── LoginResponse.java
        │   │   │       ├── RefreshSessionRequest.java
        │   │   │       ├── SocialLoginRequest.java
        │   │   │       ├── ResetPasswordRequest.java
        │   │   │       ├── ChangePasswordRequest.java
        │   │   │       ├── ChangeEmailRequest.java
        │   │   │       ├── UpdateProfileRequest.java
        │   │   │       ├── AddressRequest.java
        │   │   │       ├── UserResponse.java
        │   │   │       ├── ProfileResponse.java
        │   │   │       ├── AddressResponse.java
        │   │   │       ├── SessionResponse.java
        │   │   │       ├── RoleRequest.java
        │   │   │       ├── RoleResponse.java
        │   │   │       ├── NotificationResponse.java
        │   │   │       ├── NotificationPreferenceRequest.java
        │   │   │       └── NotificationPreferenceResponse.java
        │   │   │
        │   │   ├── catalog/
        │   │   │   ├── CategoryController.java              (#45-51)
        │   │   │   ├── BrandController.java                 (#52-56)
        │   │   │   ├── ProductController.java               (#57-69)
        │   │   │   ├── StockController.java                 (#70-75)
        │   │   │   ├── ReviewController.java                (#77-80, 84-86)
        │   │   │   ├── FavoriteController.java              (#81-83)
        │   │   │   └── dto/
        │   │   │       ├── CreateCategoryRequest.java
        │   │   │       ├── CategoryResponse.java
        │   │   │       ├── CreateBrandRequest.java
        │   │   │       ├── BrandResponse.java
        │   │   │       ├── CreateProductRequest.java
        │   │   │       ├── UpdateProductRequest.java
        │   │   │       ├── ProductResponse.java
        │   │   │       ├── ProductSearchRequest.java
        │   │   │       ├── ManageImagesRequest.java
        │   │   │       ├── AdjustStockRequest.java
        │   │   │       ├── StockMovementResponse.java
        │   │   │       ├── SubmitReviewRequest.java
        │   │   │       ├── ReviewResponse.java
        │   │   │       └── FavoriteResponse.java
        │   │   │
        │   │   ├── cart/
        │   │   │   ├── CartController.java                  (#87-92, 94)
        │   │   │   ├── SavedCartController.java             (#96-99)
        │   │   │   └── dto/
        │   │   │       ├── AddItemRequest.java
        │   │   │       ├── UpdateQuantityRequest.java
        │   │   │       ├── CartResponse.java
        │   │   │       └── SavedCartResponse.java
        │   │   │
        │   │   ├── order/
        │   │   │   ├── CheckoutController.java              (#100-103)
        │   │   │   ├── OrderController.java                 (#104-115)
        │   │   │   ├── TrackingController.java              (#116-117)
        │   │   │   ├── InvoiceController.java               (#118-119)
        │   │   │   ├── OrderRefundController.java           (#120-122)
        │   │   │   └── dto/
        │   │   │       ├── InitiateCheckoutRequest.java
        │   │   │       ├── CheckoutResponse.java
        │   │   │       ├── SelectShippingRequest.java
        │   │   │       ├── PlaceOrderRequest.java
        │   │   │       ├── OrderResponse.java
        │   │   │       ├── CancelOrderRequest.java
        │   │   │       ├── ShipOrderRequest.java
        │   │   │       ├── TrackingEventRequest.java
        │   │   │       ├── TrackingEventResponse.java
        │   │   │       ├── IssueInvoiceRequest.java
        │   │   │       ├── InvoiceResponse.java
        │   │   │       ├── RequestRefundRequest.java
        │   │   │       ├── ProcessRefundRequest.java
        │   │   │       └── OrderRefundResponse.java
        │   │   │
        │   │   ├── payment/
        │   │   │   ├── PaymentController.java               (#128-131, 133-138)
        │   │   │   ├── PaymentMethodController.java         (#123-127)
        │   │   │   ├── WebhookController.java               (#132, 146)
        │   │   │   ├── ChargebackController.java            (#140-141, 143-145)
        │   │   │   ├── PaymentRefundController.java         (#139)
        │   │   │   └── dto/
        │   │   │       ├── CreatePaymentRequest.java
        │   │   │       ├── PaymentResponse.java
        │   │   │       ├── SavePaymentMethodRequest.java
        │   │   │       ├── PaymentMethodResponse.java
        │   │   │       ├── WebhookPayload.java
        │   │   │       ├── ChargebackResponse.java
        │   │   │       ├── SubmitEvidenceRequest.java
        │   │   │       ├── ResolveChargebackRequest.java
        │   │   │       └── TransactionResponse.java
        │   │   │
        │   │   └── coupon/
        │   │       ├── CouponController.java                (#148-155, 162)
        │   │       ├── CouponCartController.java            (#156-158)
        │   │       └── dto/
        │   │           ├── CreateCouponRequest.java
        │   │           ├── UpdateCouponRequest.java
        │   │           ├── CouponResponse.java
        │   │           ├── SetEligibilityRequest.java
        │   │           └── ApplyCouponRequest.java
        │   │
        │   └── resources/
        │       ├── application.yml
        │       ├── application-dev.yml
        │       ├── application-test.yml
        │       └── application-prod.yml
        │
        └── test/java/com/ecommerce/api/
            ├── E2ETestBase.java                    (Testcontainers — app completa)
            ├── user/
            │   ├── AuthControllerTest.java
            │   ├── UserControllerTest.java
            │   └── AddressControllerTest.java
            ├── catalog/
            │   ├── ProductControllerTest.java
            │   ├── CategoryControllerTest.java
            │   └── StockControllerTest.java
            ├── cart/
            │   └── CartControllerTest.java
            ├── order/
            │   ├── CheckoutControllerTest.java
            │   └── OrderControllerTest.java
            ├── payment/
            │   ├── PaymentControllerTest.java
            │   └── WebhookControllerTest.java
            └── coupon/
                └── CouponControllerTest.java
```

---

## Grafo de Dependências entre Módulos

```
shared  ←──  domain  ←──  application  ←──  infrastructure
                                    ↑               │
                                    │               │
                                    └─── api ───────┘
```

| Módulo | Depende de |
|---|---|
| `shared` | nenhum (Java puro + Vavr) |
| `domain` | `shared` |
| `application` | `shared`, `domain` |
| `infrastructure` | `shared`, `domain`, `application`, Spring Boot starters, JPA, Flyway, Security, JJWT |
| `api` | `shared`, `domain`, `application`, `infrastructure`, Spring Boot Web, Springdoc |

---

## Cobertura por Tipo de Use Case

| Tipo | Qtd | Onde vive | Quem agenda/invoca |
|---|:---:|---|---|
| `[CMD]` | 105 | `application/` | Controllers via `api/` |
| `[QRY]` | 46 | `application/` | Controllers via `api/` |
| `[EVT]` | 2 | `application/**/event/` | `SpringDomainEventPublisher` no `infrastructure/` |
| `[JOB]` | 11 | `application/**/job/` | `ScheduledJobsConfig` no `api/` (usa `@Scheduled`) |
| | **171** | | |

---

## Notas de Design

1. **`shared`, `domain` e `application` não dependem do Spring.** Toda lógica de negócio roda com JUnit + Mockito puros.

2. **Gateways (interfaces)** vivem no `domain` — cada aggregate/entity relevante tem seu próprio Gateway. As implementações JPA ficam no `infrastructure` (DIP).

3. **Use Cases** nomeados exatamente como na lista (`RegisterUser`, `AuthenticateUser`, `PlaceOrder`, etc.) e mapeados com `#número` para rastreabilidade.

4. **Commands e Outputs** vivem ao lado de cada use case. Commands são imutáveis (records). Outputs são DTOs de saída da camada application — distintos dos DTOs HTTP do `api/`.

5. **Event Handlers** (`[EVT]`) ficam em `application/**/event/`. São invocados pela infra de eventos (não por controllers).

6. **Jobs** (`[JOB]`) ficam em `application/**/job/`. O `api/ScheduledJobsConfig` agenda via `@Scheduled` e delega para o use case.

7. **Flyway migrations** separadas por responsabilidade: schemas, funções, enums, tabelas por módulo, FKs tardias e partições.

8. **Controllers no `api/`** foram subdivididos para respeitar o SRP: `AuthController`, `PasswordController`, `ProfileController`, `CheckoutController`, `OrderController`, `WebhookController`, etc.

9. **Testes**: unitários no `domain/` e `application/` (sem Spring), integração no `infrastructure/` (Testcontainers), E2E no `api/` (app completa).