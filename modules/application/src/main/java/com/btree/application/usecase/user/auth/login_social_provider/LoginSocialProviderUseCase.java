package com.btree.application.usecase.user.auth.login_social_provider;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserSocialLogin;
import com.btree.domain.user.error.AuthError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.SocialProviderGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserSocialLoginGateway;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.domain.user.valueobject.SocialUserProfile;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class LoginSocialProviderUseCase implements UseCase<LoginSocialProviderCommand, LoginSocialProviderOutput> {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google");

    private final UserGateway userGateway;
    private final SessionGateway sessionGateway;
    private final UserSocialLoginGateway userSocialLoginGateway;
    private final SocialProviderGateway socialProviderGateway;
    private final TokenProvider tokenProvider;
    private final TokenHasher tokenHasher;
    private final TransactionManager transactionManager;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public LoginSocialProviderUseCase(UserGateway userGateway, SessionGateway sessionGateway, UserSocialLoginGateway userSocialLoginGateway, SocialProviderGateway socialProviderGateway, TokenProvider tokenProvider, TokenHasher tokenHasher, TransactionManager transactionManager, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this.userGateway = userGateway;
        this.sessionGateway = sessionGateway;
        this.userSocialLoginGateway = userSocialLoginGateway;
        this.socialProviderGateway = socialProviderGateway;
        this.tokenProvider = tokenProvider;
        this.tokenHasher = tokenHasher;
        this.transactionManager = transactionManager;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    public Either<Notification, LoginSocialProviderOutput> execute(LoginSocialProviderCommand loginSocialProviderCommand) {
        final var notification = Notification.create();

        // validar presenca dos campos obrigatorios
        if(loginSocialProviderCommand.provider() == null || loginSocialProviderCommand.provider().isBlank() || loginSocialProviderCommand.token() == null || loginSocialProviderCommand.token().isBlank()){
            notification.append(AuthError.INVALID_SOCIAL_TOKEN);
            return Left(notification);
        }

        // verificar se o provedor e suportado
        final String provider = loginSocialProviderCommand.provider().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(provider)){
            notification.append(AuthError.UNSUPPORTED_PROVIDER);
            return Left(notification);
        }

        // validar token no provedor externo e extrair perfil
        final var profileOpt = this.socialProviderGateway.validateTokenAndGetProfile(provider, loginSocialProviderCommand.token());
        if(profileOpt.isEmpty()){
            notification.append(AuthError.INVALID_SOCIAL_TOKEN);
            return Left(notification);
        }

        final SocialUserProfile profile = profileOpt.get();

        // resolver qual fluxo usar e preparar dados para a transacao
        final var socialLinkOpt = this.userSocialLoginGateway.findByProviderAndProviderUserId(provider, profile.providerUserId());

        // decisao: LINK_EXISTS | EMAIL_EXISTS | NEW_USER
        final Flow flow;
        final User preResolvedUser; // null se for novo usuario

        if(socialLinkOpt.isPresent()){
            // fluxo 1 - vinculo ja existe
            final var userOpt = this.userGateway.findById(socialLinkOpt.get().getUserId());
            if(userOpt.isEmpty()){
                notification.append(AuthError.INVALID_SOCIAL_TOKEN);
            }
            flow = Flow.LINK_EXISTS;
            preResolvedUser = userOpt.get();
        }else{
            final var byEmailOpt = this.userGateway.findByEmail(profile.email().toLowerCase());
            if(byEmailOpt.isPresent()){
                // fluxo 2 - email ja cadastrado, sem vinculo
                flow = Flow.EMAIL_EXISTS;
                preResolvedUser = byEmailOpt.get();
            } else {
                // fluxo 3 - novo usuario
                flow = Flow.NEW_USER;
                preResolvedUser = null;
            }
        }

        // preparar tokens e sessao (os ids de sessao sao gerados aqui, fora da transacao)
        final var now = Instant.now();
        final var accessTokenExpiresAt = now.plusMillis(accessTokenExpirationMs);
        final var refreshTokenExpiresAt = now.plusMillis(refreshTokenExpirationMs);

        final var rawRefreshToken = tokenHasher.generate();
        final var refreshTokenHash = tokenHasher.hash(rawRefreshToken);
        final var deviceInfo = DeviceInfo.of(loginSocialProviderCommand.ipAddress(), loginSocialProviderCommand.userAgent());
        final var displayName = buildDisplayName(profile);

        // executar atomicamente
        return Try(() -> this.transactionManager.execute(() -> {
            // garantir o user persistido
            final User user;
            if(flow == Flow.NEW_USER){
                final String username = deriveUsername(profile);
                final User newUser = User.createFromSocial(username, profile.email().toLowerCase(),Notification.create());
                user = this.userGateway.save(newUser);
                this.userGateway.assignRole(user.getId(), "customer");
            } else {
                user = preResolvedUser;
            }

            // Criar vínculo social se necessário (Fluxo 2 e 3)
            if (flow != Flow.LINK_EXISTS) {
                final var socialLink = UserSocialLogin.create(
                        user.getId(), provider, profile.providerUserId(), displayName
                );
                this.userSocialLoginGateway.create(socialLink);
            }

            // Criar sessão
            final var session = Session.create(
                    user.getId(),
                    refreshTokenHash,
                    deviceInfo,
                    refreshTokenExpiresAt,
                    Notification.create()
            );
            this.sessionGateway.create(session);


            // Gerar access token com claims do usuário persistido
            final var accessToken = tokenProvider.generate(
                    user.getId().getValue().toString(),
                    Map.of("username", user.getUsername(), "email", user.getEmail()),
                    accessTokenExpiresAt
            );

            return new LoginSocialProviderOutput(
                    accessToken,
                    rawRefreshToken,
                    accessTokenExpiresAt,
                    user.getId().getValue().toString(),
                    user.getUsername(),
                    user.getEmail(),
                    List.copyOf(user.getRoles())
            );
        })).toEither().mapLeft(Notification::create);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private enum Flow { LINK_EXISTS, EMAIL_EXISTS, NEW_USER }

    /**
     * Deriva username a partir dos dados do perfil social.
     * Usa firstName+lastName se disponíveis, senão o prefixo do e-mail.
     * Adiciona sufixo aleatório de 4 dígitos para reduzir colisões.
     */
    private String deriveUsername(final SocialUserProfile profile) {
        String base;
        if (profile.firstName() != null && !profile.firstName().isBlank()) {
            final String last = profile.lastName() != null ? profile.lastName() : "";
            base = (profile.firstName() + last).toLowerCase().replaceAll("[^a-z0-9]", "");
        } else {
            base = profile.email().split("@")[0].toLowerCase().replaceAll("[^a-z0-9._]", "");
        }
        if (base.length() > 46) base = base.substring(0, 46);
        if (base.length() < 3) base = "user" + base;
        return base + (int) (Math.random() * 9000 + 1000);
    }

    private String buildDisplayName(final SocialUserProfile profile) {
        if (profile.firstName() == null) return null;
        final String last = profile.lastName() != null ? " " + profile.lastName() : "";
        return (profile.firstName() + last).trim();
    }
}
