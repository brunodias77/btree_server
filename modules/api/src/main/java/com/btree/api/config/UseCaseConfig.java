package com.btree.api.config;

import com.btree.application.usecase.job.clean_expired_tokens.CleanupExpiredTokensJob;
import com.btree.application.usecase.job.process_domain_event.ProcessDomainEventsJob;
import com.btree.application.usecase.job.retry_failed_event.RetryFailedEventsJob;
import com.btree.application.usecase.user.auth.login.LoginUserUseCase;
import com.btree.application.usecase.user.auth.logout.LogoutUserUseCase;
import com.btree.application.usecase.user.auth.refresh.RefreshSessionUseCase;
import com.btree.application.usecase.user.auth.register.RegisterUserUseCase;
import com.btree.application.usecase.user.auth.verify_email.VerifyEmailUseCase;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.infrastructure.config.JwtConfig;
import com.btree.shared.contract.*;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.event.IntegrationEventPublisher;
import com.btree.shared.gateway.OutboxEventGateway;
import com.btree.shared.gateway.ProcessedEventGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra todos os Use Cases da aplicação como beans Spring.
 *
 * <p>Cada Use Case recebe seus Gateways (e demais dependências) via construtor,
 * seguindo injeção manual — sem {@code @Autowired} nos módulos {@code application/}
 * ou {@code domain/}. O wiring completo acontece exclusivamente aqui.
 *
 * <p>Convenção de organização: um {@code @Bean} por Use Case, agrupados por
 * contexto de negócio (Users, Catalog, Cart, Orders, Payments, Coupons, Shared).
 *
 * @see com.btree.shared.usecase.UseCase
 * @see com.btree.shared.usecase.UnitUseCase
 * @see com.btree.shared.usecase.QueryUseCase
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    // ── Users ─────────────────────────────────────────────────────────────────

    @Bean
    public RegisterUserUseCase registerUserUseCase(
            final UserGateway userGateway,
            final UserTokenGateway userTokenGateway,
            final PasswordHasher passwordHasher,
            final TokenHasher tokenHasher,
            final EmailService emailService,
            final DomainEventPublisher eventPublisher,
            final IntegrationEventPublisher integrationEventPublisher,
            final TransactionManager transactionManager
    ) {
        return new RegisterUserUseCase(
                userGateway, userTokenGateway, passwordHasher,
                tokenHasher, eventPublisher, integrationEventPublisher, transactionManager, emailService
        );
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(
            final UserGateway userGateway,
            final SessionGateway sessionGateway,
            final UserTokenGateway userTokenGateway,
            final LoginHistoryGateway loginHistoryGateway,
            final PasswordHasher passwordHasher,
            final TokenProvider tokenProvider,
            final TokenHasher tokenHasher,
            final TransactionManager transactionManager,
            final DomainEventPublisher eventPublisher,
            final JwtConfig jwtConfig
    ){
        return new LoginUserUseCase(
                userGateway,
                sessionGateway,
                userTokenGateway,
                loginHistoryGateway,
                passwordHasher,
                tokenProvider,
                tokenHasher,
                transactionManager,
                eventPublisher,
                jwtConfig.getAccessTokenExpirationMs(),
                jwtConfig.getRefreshTokenExpirationMs()
        );
    }



    @Bean
    public VerifyEmailUseCase verifyEmailUseCase(
            final UserTokenGateway userTokenGateway,
            final UserGateway userGateway,
            final TokenHasher tokenHasher,
            final TransactionManager transactionManager
    ) {
        return new VerifyEmailUseCase(userTokenGateway, userGateway, tokenHasher, transactionManager);
    }

    @Bean
    public RefreshSessionUseCase refreshSessionUseCase(
            final SessionGateway sessionGateway,
            final UserGateway userGateway,
            final TokenProvider tokenProvider,
            final TokenHasher tokenHasher,
            final TransactionManager transactionManager,
            final JwtConfig jwtConfig
    ) {
        return new RefreshSessionUseCase(
                sessionGateway,
                userGateway,
                tokenProvider,
                tokenHasher,
                transactionManager,
                jwtConfig.getAccessTokenExpirationMs(),
                jwtConfig.getRefreshTokenExpirationMs()
        );
    }

    @Bean
    public LogoutUserUseCase logoutUserUseCase(
            final SessionGateway sessionGateway,
            final TokenHasher tokenHasher,
            final TransactionManager transactionManager
    ) {
        return new LogoutUserUseCase(sessionGateway, tokenHasher, transactionManager);
    }

//    @Bean
//    public LogoutAllSessionsUseCase logoutAllSessionsUseCase(
//            final SessionGateway sessionGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new LogoutAllSessionsUseCase(sessionGateway, transactionManager);
//    }
//
//    @Bean
//    public GetCurrentUserUseCase getCurrentUserUseCase(final UserGateway userGateway) {
//        return new GetCurrentUserUseCase(userGateway);
//    }
//
//    @Bean
//    public RequestPasswordResetUseCase requestPasswordResetUseCase(
//            final UserGateway userGateway,
//            final UserTokenGateway userTokenGateway,
//            final TokenHasher tokenHasher,
//            final EmailService emailService,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new RequestPasswordResetUseCase(
//                userGateway, userTokenGateway, tokenHasher,
//                emailService, eventPublisher, transactionManager
//        );
//    }
//
//    @Bean
//    public LoginWithSocialProviderUseCase loginWithSocialProviderUseCase(
//            final UserGateway userGateway,
//            final SessionGateway sessionGateway,
//            final UserSocialLoginGateway userSocialLoginGateway,
//            final SocialProviderGateway socialProviderGateway,
//            final TokenProvider tokenProvider,
//            final TokenHasher tokenHasher,
//            final TransactionManager transactionManager,
//            final JwtConfig jwtConfig
//    ) {
//        return new LoginWithSocialProviderUseCase(
//                userGateway,
//                sessionGateway,
//                userSocialLoginGateway,
//                socialProviderGateway,
//                tokenProvider,
//                tokenHasher,
//                transactionManager,
//                jwtConfig.getAccessTokenExpirationMs(),
//                jwtConfig.getRefreshTokenExpirationMs()
//        );
//    }
//
//    @Bean
//    public SetupTwoFactorUseCase setupTwoFactorUseCase(
//            final UserGateway userGateway,
//            final UserTokenGateway userTokenGateway,
//            final TotpGateway totpGateway
//    ) {
//        return new SetupTwoFactorUseCase(userGateway, userTokenGateway, totpGateway);
//    }
//
//    @Bean
//    public EnableTwoFactorUseCase enableTwoFactorUseCase(
//            final UserGateway userGateway,
//            final UserTokenGateway userTokenGateway,
//            final TotpGateway totpGateway,
//            final TransactionManager transactionManager,
//            final DomainEventPublisher eventPublisher
//    ) {
//        return new EnableTwoFactorUseCase(userGateway, userTokenGateway, totpGateway, transactionManager, eventPublisher);
//    }
//
//    @Bean
//    public VerifyTwoFactorUseCase verifyTwoFactorUseCase(
//            final UserTokenGateway userTokenGateway,
//            final UserGateway userGateway,
//            final SessionGateway sessionGateway,
//            final LoginHistoryGateway loginHistoryGateway,
//            final TotpGateway totpGateway,
//            final TokenProvider tokenProvider,
//            final TokenHasher tokenHasher,
//            final TransactionManager transactionManager,
//            final DomainEventPublisher eventPublisher,
//            final JwtConfig jwtConfig
//    ) {
//        return new VerifyTwoFactorUseCase(
//                userTokenGateway,
//                userGateway,
//                sessionGateway,
//                loginHistoryGateway,
//                totpGateway,
//                tokenProvider,
//                tokenHasher,
//                transactionManager,
//                eventPublisher,
//                jwtConfig.getAccessTokenExpirationMs(),
//                jwtConfig.getRefreshTokenExpirationMs()
//        );
//    }
//
//    @Bean
//    public CleanupExpiredTokensUseCase cleanupExpiredTokensUseCase(
//            final UserTokenGateway userTokenGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new CleanupExpiredTokensUseCase(userTokenGateway, transactionManager);
//    }
//
//    @Bean
//    public GetProfileUseCase getProfileUseCase(final ProfileGateway profileGateway) {
//        return new GetProfileUseCase(profileGateway);
//    }
//
//    @Bean
//    public UpdateProfileUseCase updateProfileUseCase(
//            final ProfileGateway profileGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateProfileUseCase(profileGateway, transactionManager);
//    }
//
//    @Bean
//    public AddAddressUseCase addAddressUseCase(
//            final AddressGateway addressGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new AddAddressUseCase(addressGateway, transactionManager);
//    }
//
//    @Bean
//    public UpdateAddressUseCase updateAddressUseCase(
//            final AddressGateway addressGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateAddressUseCase(addressGateway, transactionManager);
//    }
//
//    @Bean
//    public DeleteAddressUseCase deleteAddressUseCase(
//            final AddressGateway addressGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new DeleteAddressUseCase(addressGateway, transactionManager);
//    }
//
//    @Bean
//    public SetDefaultAddressUseCase setDefaultAddressUseCase(
//            final AddressGateway addressGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new SetDefaultAddressUseCase(addressGateway, transactionManager);
//    }
//
//    @Bean
//    public ListAddressesUseCase listAddressesUseCase(final AddressGateway addressGateway) {
//        return new ListAddressesUseCase(addressGateway);
//    }
//
//    // ── Catalog ───────────────────────────────────────────────────────────────
//
//    @Bean
//    public CreateBrandUseCase createBrandUseCase(
//            final BrandGateway brandGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new CreateBrandUseCase(brandGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public UpdateBrandUseCase updateBrandUseCase(
//            final BrandGateway brandGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateBrandUseCase(brandGateway, transactionManager);
//    }
//
//    @Bean
//    public CreateCategoryUseCase createCategoryUseCase(
//            final CategoryGateway categoryGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new CreateCategoryUseCase(categoryGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public UpdateCategoryUseCase updateCategoryUseCase(
//            final CategoryGateway categoryGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateCategoryUseCase(categoryGateway, transactionManager);
//    }
//
//    @Bean
//    public ListCategoriesUseCase listCategoriesUseCase(final CategoryGateway categoryGateway) {
//        return new ListCategoriesUseCase(categoryGateway);
//    }
//
//    @Bean
//    public GetCategoryUseCase getCategoryUseCase(final CategoryGateway categoryGateway) {
//        return new GetCategoryUseCase(categoryGateway);
//    }
//
//    @Bean
//    public CreateProductUseCase createProductUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new CreateProductUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public UpdateProductUseCase updateProductUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateProductUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public PublishProductUseCase publishProductUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new PublishProductUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public PauseProductUseCase pauseProductUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new PauseProductUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public ArchiveProductUseCase archiveProductUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new ArchiveProductUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public GetProductUseCase getProductUseCase(
//            final ProductGateway productGateway,
//            final CategoryGateway categoryGateway,
//            final BrandGateway brandGateway
//    ) {
//        return new GetProductUseCase(productGateway, categoryGateway, brandGateway);
//    }
//
//    @Bean
//    public SearchProductsUseCase searchProductsUseCase(final ProductGateway productGateway) {
//        return new SearchProductsUseCase(productGateway);
//    }
//
//    @Bean
//    public ListProductsByCategoryUseCase listProductsByCategoryUseCase(
//            final ProductGateway productGateway,
//            final CategoryGateway categoryGateway
//    ) {
//        return new ListProductsByCategoryUseCase(productGateway, categoryGateway);
//    }
//
//    @Bean
//    public AddProductImageUseCase addProductImageUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new AddProductImageUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public RemoveProductImageUseCase removeProductImageUseCase(
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new RemoveProductImageUseCase(productGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public SetPrimaryProductImageUseCase setPrimaryProductImageUseCase(
//            final ProductGateway productGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new SetPrimaryProductImageUseCase(productGateway, transactionManager);
//    }
//
//    @Bean
//    public ReorderProductImagesUseCase reorderProductImagesUseCase(
//            final ProductGateway productGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new ReorderProductImagesUseCase(productGateway, transactionManager);
//    }
//
//    @Bean
//    public ListFeaturedProductsUseCase listFeaturedProductsUseCase(
//            final ProductGateway productGateway
//    ) {
//        return new ListFeaturedProductsUseCase(productGateway);
//    }
//
//    @Bean
//    public ListProductsByBrandUseCase listProductsByBrandUseCase(
//            final ProductGateway productGateway,
//            final BrandGateway brandGateway
//    ) {
//        return new ListProductsByBrandUseCase(productGateway, brandGateway);
//    }
//
//    @Bean
//    public AdjustStockUseCase adjustStockUseCase(
//            final ProductGateway productGateway,
//            final StockMovementGateway stockMovementGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new AdjustStockUseCase(
//                productGateway, stockMovementGateway, eventPublisher, transactionManager);
//    }
//
//    @Bean
//    public ReserveStockUseCase reserveStockUseCase(
//            final ProductGateway productGateway,
//            final StockReservationGateway stockReservationGateway,
//            final StockMovementGateway stockMovementGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new ReserveStockUseCase(
//                productGateway, stockReservationGateway, stockMovementGateway,
//                eventPublisher, transactionManager);
//    }
//
//    // ── Shared Jobs ───────────────────────────────────────────────────────────
//
//    @Bean
//    public ProcessDomainEventsUseCase processDomainEventsUseCase(
//            final OutboxEventGateway outboxEventGateway,
//            final ProcessedEventGateway processedEventGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new ProcessDomainEventsUseCase(outboxEventGateway, processedEventGateway, transactionManager);
//    }
//
//    @Bean
//    public RetryFailedEventsUseCase retryFailedEventsUseCase(
//            final OutboxEventGateway outboxEventGateway,
//            final ProcessedEventGateway processedEventGateway,
//            final TransactionManager transactionManager
//    ) {
//        return new RetryFailedEventsUseCase(outboxEventGateway, processedEventGateway, transactionManager);
//    }
//
//    // TODO: DeleteCategoryUseCase...
//
//    // ── Cart ──────────────────────────────────────────────────────────────────
//
//    @Bean
//    public AddItemToCartUseCase addItemToCartUseCase(
//            final CartGateway cartGateway,
//            final CartActivityLogGateway cartActivityLogGateway,
//            final ProductGateway productGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new AddItemToCartUseCase(
//                cartGateway,
//                cartActivityLogGateway,
//                productGateway,
//                eventPublisher,
//                transactionManager
//        );
//    }
//
//    @Bean
//    public UpdateCartItemQuantityUseCase updateCartItemQuantityUseCase(
//            final CartGateway cartGateway,
//            final CartActivityLogGateway cartActivityLogGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new UpdateCartItemQuantityUseCase(
//                cartGateway,
//                cartActivityLogGateway,
//                eventPublisher,
//                transactionManager
//        );
//    }
//
//    @Bean
//    public RemoveItemFromCartUseCase removeItemFromCartUseCase(
//            final CartGateway cartGateway,
//            final CartActivityLogGateway cartActivityLogGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new RemoveItemFromCartUseCase(
//                cartGateway,
//                cartActivityLogGateway,
//                eventPublisher,
//                transactionManager
//        );
//    }
//
//    @Bean
//    public GetCartUseCase getCartUseCase(
//            final CartGateway cartGateway,
//            final ProductGateway productGateway
//    ) {
//        return new GetCartUseCase(cartGateway, productGateway);
//    }
//
//    @Bean
//    public MergeGuestCartUseCase mergeGuestCartUseCase(
//            final CartGateway cartGateway,
//            final CartActivityLogGateway cartActivityLogGateway,
//            final ProductGateway productGateway,
//            final StockReservationGateway stockReservationGateway,
//            final DomainEventPublisher eventPublisher,
//            final TransactionManager transactionManager
//    ) {
//        return new MergeGuestCartUseCase(
//                cartGateway,
//                cartActivityLogGateway,
//                productGateway,
//                stockReservationGateway,
//                eventPublisher,
//                transactionManager
//        );
//    }

    // ── Orders ────────────────────────────────────────────────────────────────
    // TODO: PlaceOrderUseCase, CancelOrderUseCase, ConfirmOrderUseCase...

    // ── Payments ──────────────────────────────────────────────────────────────
    // TODO: CreatePaymentUseCase, AuthorizePaymentUseCase, CapturePaymentUseCase...

    // ── Coupons ───────────────────────────────────────────────────────────────
    // TODO: ValidateCouponUseCase, ApplyCouponToCartUseCase...

    // ── Shared / Jobs ─────────────────────────────────────────────────────────

    @Bean
    public CleanupExpiredTokensJob cleanupExpiredTokensJob(
            final UserTokenGateway userTokenGateway,
            final TransactionManager transactionManager
    ) {
        return new CleanupExpiredTokensJob(userTokenGateway, transactionManager);
    }

    @Bean
    public ProcessDomainEventsJob processDomainEventsJob(
            final OutboxEventGateway outboxEventGateway,
            final ProcessedEventGateway processedEventGateway,
            final TransactionManager transactionManager
    ) {
        return new ProcessDomainEventsJob(outboxEventGateway, processedEventGateway, transactionManager);
    }

    @Bean
    public RetryFailedEventsJob retryFailedEventsJob(
            final OutboxEventGateway outboxEventGateway,
            final ProcessedEventGateway processedEventGateway,
            final TransactionManager transactionManager
    ) {
        return new RetryFailedEventsJob(outboxEventGateway, processedEventGateway, transactionManager);
    }
}
