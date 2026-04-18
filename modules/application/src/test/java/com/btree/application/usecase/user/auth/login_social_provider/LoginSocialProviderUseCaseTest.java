package com.btree.application.usecase.user.auth.login_social_provider;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.entity.UserSocialLogin;
import com.btree.domain.user.error.AuthError;
import com.btree.domain.user.gateway.SessionGateway;
import com.btree.domain.user.gateway.SocialProviderGateway;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.gateway.UserSocialLoginGateway;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserSocialLoginId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.domain.user.valueobject.SocialUserProfile;
import com.btree.shared.contract.TokenHasher;
import com.btree.shared.contract.TokenProvider;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginSocialProviderUseCase")
class LoginSocialProviderUseCaseTest {

    @Mock UserGateway userGateway;
    @Mock SessionGateway sessionGateway;
    @Mock UserSocialLoginGateway userSocialLoginGateway;
    @Mock SocialProviderGateway socialProviderGateway;
    @Mock TokenProvider tokenProvider;
    @Mock TokenHasher tokenHasher;
    @Mock TransactionManager transactionManager;

    LoginSocialProviderUseCase useCase;

    private static final long ACCESS_TOKEN_MS  = 900_000L;
    private static final long REFRESH_TOKEN_MS = 604_800_000L;

    @BeforeEach
    void setUp() {
        useCase = new LoginSocialProviderUseCase(
                userGateway, sessionGateway, userSocialLoginGateway, socialProviderGateway,
                tokenProvider, tokenHasher, transactionManager,
                ACCESS_TOKEN_MS, REFRESH_TOKEN_MS
        );
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SocialUserProfile profileComNome() {
        return new SocialUserProfile("gid-123", "joao@gmail.com", "João", "Silva", "https://pic.url");
    }

    private SocialUserProfile profileSemNome() {
        return new SocialUserProfile("gid-123", "joao@gmail.com", null, null, null);
    }

    private User buildUser() {
        return User.with(
                UserId.unique(), "joaosilva1234", "joao@gmail.com",
                true, null,
                null, false, false, null,
                false, null, 0, true,
                Instant.now(), Instant.now(), 0, null, null
        );
    }

    private UserSocialLogin buildLink(final UserId userId) {
        return UserSocialLogin.with(
                UserSocialLoginId.unique(), userId,
                "google", "gid-123", "João Silva",
                Instant.now()
        );
    }

    private Session buildSession(final UserId userId) {
        return Session.with(
                SessionId.unique(), userId, "hashed-refresh",
                DeviceInfo.of("127.0.0.1", "Mozilla"),
                Instant.now().plusSeconds(REFRESH_TOKEN_MS / 1000),
                false, Instant.now(), Instant.now(), 0
        );
    }

    private LoginSocialProviderCommand command(final String provider, final String token) {
        return new LoginSocialProviderCommand(provider, token, "127.0.0.1", "Mozilla/5.0");
    }

    private void stubTokens() {
        when(tokenHasher.generate()).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("hashed-refresh");
        when(tokenProvider.generate(anyString(), any(), any())).thenReturn("access-token");
    }

    private String firstError(final Notification notification) {
        return notification.firstError() != null ? notification.firstError().message() : null;
    }

    // ── validação de entrada ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validação do command")
    class ValidacaoDoCommand {

        @Test
        @DisplayName("Deve retornar erro quando o provider for nulo")
        void givenNullProvider_whenExecute_thenReturnInvalidSocialToken() {
            final var result = useCase.execute(command(null, "valid-token"));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verifyNoInteractions(socialProviderGateway, userSocialLoginGateway, userGateway);
        }

        @Test
        @DisplayName("Deve retornar erro quando o provider estiver em branco")
        void givenBlankProvider_whenExecute_thenReturnInvalidSocialToken() {
            final var result = useCase.execute(command("   ", "valid-token"));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verifyNoInteractions(socialProviderGateway);
        }

        @Test
        @DisplayName("Deve retornar erro quando o token for nulo")
        void givenNullToken_whenExecute_thenReturnInvalidSocialToken() {
            final var result = useCase.execute(command("google", null));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verifyNoInteractions(socialProviderGateway);
        }

        @Test
        @DisplayName("Deve retornar erro quando o token estiver em branco")
        void givenBlankToken_whenExecute_thenReturnInvalidSocialToken() {
            final var result = useCase.execute(command("google", "   "));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verifyNoInteractions(socialProviderGateway);
        }

        @Test
        @DisplayName("Deve retornar erro quando o provider nao for suportado")
        void givenUnsupportedProvider_whenExecute_thenReturnUnsupportedProvider() {
            final var result = useCase.execute(command("facebook", "valid-token"));

            assertTrue(result.isLeft());
            assertEquals(AuthError.UNSUPPORTED_PROVIDER.message(), firstError(result.getLeft()));
            verifyNoInteractions(socialProviderGateway);
        }

        @Test
        @DisplayName("Deve normalizar o provider para minusculo antes de verificar suporte")
        void givenUppercaseProvider_whenExecute_thenNormalizeAndProceed() {
            when(socialProviderGateway.validateTokenAndGetProfile(eq("google"), anyString()))
                    .thenReturn(Optional.empty());

            final var result = useCase.execute(command("GOOGLE", "id-token"));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verify(socialProviderGateway).validateTokenAndGetProfile("google", "id-token");
        }

        @Test
        @DisplayName("Deve retornar erro quando o provedor externo rejeitar o token")
        void givenRejectedToken_whenProviderReturnsEmpty_thenReturnInvalidSocialToken() {
            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            final var result = useCase.execute(command("google", "expired-token"));

            assertTrue(result.isLeft());
            assertEquals(AuthError.INVALID_SOCIAL_TOKEN.message(), firstError(result.getLeft()));
            verifyNoInteractions(userSocialLoginGateway, userGateway);
        }
    }

    // ── fluxo LINK_EXISTS ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fluxo LINK_EXISTS — vinculo social ja existe")
    class FluxoLinkExists {

        @Test
        @DisplayName("Deve retornar tokens quando o vinculo social ja existir")
        void givenExistingSocialLink_whenExecute_thenReturnTokens() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId("google", "gid-123"))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(user.getId())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals("access-token", output.accessToken());
            assertEquals("raw-refresh", output.refreshToken());
            assertEquals(user.getId().getValue().toString(), output.userId());
            assertEquals(user.getUsername(), output.username());
            assertEquals(user.getEmail(), output.email());
            assertNotNull(output.accessTokenExpiresAt());
        }

        @Test
        @DisplayName("Nao deve salvar novo usuario quando o vinculo social ja existir")
        void givenExistingSocialLink_whenExecute_thenNotSaveNewUser() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userGateway, never()).save(any());
            verify(userGateway, never()).assignRole(any(), anyString());
        }

        @Test
        @DisplayName("Nao deve criar novo vinculo quando o vinculo social ja existir")
        void givenExistingSocialLink_whenExecute_thenNotCreateNewLink() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userSocialLoginGateway, never()).create(any());
        }
    }

    // ── fluxo EMAIL_EXISTS ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fluxo EMAIL_EXISTS — email cadastrado sem vinculo social")
    class FluxoEmailExists {

        @Test
        @DisplayName("Deve retornar tokens quando o email ja estiver cadastrado mas sem vinculo")
        void givenExistingEmailWithoutLink_whenExecute_thenReturnTokens() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail("joao@gmail.com")).thenReturn(Optional.of(user));
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isRight());
            assertEquals(user.getId().getValue().toString(), result.get().userId());
        }

        @Test
        @DisplayName("Deve criar vinculo social ao encontrar email cadastrado sem vinculo")
        void givenExistingEmailWithoutLink_whenExecute_thenCreateSocialLink() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.of(user));
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userSocialLoginGateway).create(any());
        }

        @Test
        @DisplayName("Nao deve salvar novo usuario quando o email ja estiver cadastrado")
        void givenExistingEmailWithoutLink_whenExecute_thenNotSaveNewUser() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.of(user));
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userGateway, never()).save(any());
            verify(userGateway, never()).assignRole(any(), anyString());
        }
    }

    // ── fluxo NEW_USER ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fluxo NEW_USER — primeiro acesso via social")
    class FluxoNewUser {

        @Test
        @DisplayName("Deve salvar novo usuario quando o email nao estiver cadastrado")
        void givenNewEmail_whenExecute_thenSaveNewUser() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(any())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isRight());
            verify(userGateway).save(any());
        }

        @Test
        @DisplayName("Deve atribuir papel customer ao novo usuario criado via login social")
        void givenNewEmail_whenExecute_thenAssignCustomerRole() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(any())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userGateway).assignRole(eq(user.getId()), eq("customer"));
        }

        @Test
        @DisplayName("Deve criar vinculo social para o novo usuario")
        void givenNewEmail_whenExecute_thenCreateSocialLink() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(any())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(userSocialLoginGateway).create(any());
        }

        @Test
        @DisplayName("Deve derivar username do nome do perfil social quando disponivel")
        void givenProfileWithName_whenExecute_thenUsernameContainsNamePrefix() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());
            final var captor  = ArgumentCaptor.forClass(User.class);

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(captor.capture())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            // "João" + "Silva" → letras ASCII apenas → base + sufixo de 4 dígitos
            final var username = captor.getValue().getUsername();
            assertNotNull(username);
            assertTrue(username.matches("[a-z0-9._]+\\d{4}"),
                    "Username deve seguir o padrão: base-alfanumérica + sufixo de 4 dígitos, obtido: " + username);
        }

        @Test
        @DisplayName("Deve derivar username do prefixo do email quando o nome nao estiver disponivel")
        void givenProfileWithoutName_whenExecute_thenUsernameStartsWithEmailPrefix() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());
            final var captor  = ArgumentCaptor.forClass(User.class);

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileSemNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(captor.capture())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            // "joao@gmail.com" → prefixo "joao" + sufixo de 4 dígitos
            final var username = captor.getValue().getUsername();
            assertTrue(username.startsWith("joao"),
                    "Username deve iniciar com o prefixo do email 'joao', obtido: " + username);
            assertTrue(username.matches("joao\\d{4}"),
                    "Username deve ser prefixo do email + 4 dígitos, obtido: " + username);
        }

        @Test
        @DisplayName("Deve retornar dados do novo usuario no output")
        void givenNewEmail_whenExecute_thenReturnNewUserDataInOutput() {
            final var user    = buildUser();
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userGateway.save(any())).thenReturn(user);
            doNothing().when(userGateway).assignRole(any(), anyString());
            when(userSocialLoginGateway.create(any())).thenReturn(buildLink(user.getId()));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isRight());
            final var output = result.get();
            assertEquals("access-token", output.accessToken());
            assertEquals("raw-refresh", output.refreshToken());
            assertEquals(user.getId().getValue().toString(), output.userId());
            assertEquals(user.getUsername(), output.username());
            assertEquals(user.getEmail(), output.email());
        }
    }

    // ── sessão e tokens ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gerenciamento de sessão e tokens")
    class SessaoETokens {

        @Test
        @DisplayName("Deve sempre criar sessao independente do fluxo")
        void givenAnyFlow_whenExecute_thenCreateSession() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(sessionGateway).create(any());
        }

        @Test
        @DisplayName("Deve gerar e fazer hash do refresh token")
        void givenSuccessfulLogin_whenExecute_thenGenerateAndHashRefreshToken() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(tokenHasher).generate();
            verify(tokenHasher).hash("raw-refresh");
        }

        @Test
        @DisplayName("Deve gerar access token com o ID do usuario como subject")
        void givenSuccessfulLogin_whenExecute_thenGenerateAccessTokenWithUserId() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            useCase.execute(command("google", "id-token"));

            verify(tokenProvider).generate(
                    eq(user.getId().getValue().toString()),
                    any(),
                    any()
            );
        }

        @Test
        @DisplayName("O access token deve expirar dentro do prazo configurado")
        void givenSuccessfulLogin_whenExecute_thenAccessTokenExpiresAtConfiguredOffset() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var before = Instant.now();
            final var result = useCase.execute(command("google", "id-token"));
            final var after  = Instant.now();

            assertTrue(result.isRight());
            final var expiresAt = result.get().accessTokenExpiresAt();
            assertTrue(expiresAt.isAfter(before.plusMillis(ACCESS_TOKEN_MS - 1_000)));
            assertTrue(expiresAt.isBefore(after.plusMillis(ACCESS_TOKEN_MS + 1_000)));
        }

        @Test
        @DisplayName("Deve retornar o raw refresh token (nao o hash) no output")
        void givenSuccessfulLogin_whenExecute_thenOutputContainsRawRefreshToken() {
            final var user    = buildUser();
            final var link    = buildLink(user.getId());
            final var session = buildSession(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            stubTokens();
            when(sessionGateway.create(any())).thenReturn(session);

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isRight());
            assertEquals("raw-refresh", result.get().refreshToken());
        }
    }

    // ── tratamento de exceção ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Tratamento de exceções")
    class TratamentoDeExcecoes {

        @Test
        @DisplayName("Deve retornar Left quando a transacao lancar excecao inesperada")
        void givenTransactionThrowsException_whenExecute_thenReturnLeft() {
            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(userGateway.findByEmail(anyString())).thenReturn(Optional.empty());
            lenient().when(tokenHasher.generate()).thenReturn("raw-refresh");
            lenient().when(tokenHasher.hash(anyString())).thenReturn("hashed-refresh");
            doThrow(new RuntimeException("Falha no banco")).when(transactionManager).execute(any());

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isLeft());
        }

        @Test
        @DisplayName("Deve retornar Left quando o gateway de sessao lancar excecao")
        void givenSessionGatewayThrows_whenExecute_thenReturnLeft() {
            final var user = buildUser();
            final var link = buildLink(user.getId());

            when(socialProviderGateway.validateTokenAndGetProfile(anyString(), anyString()))
                    .thenReturn(Optional.of(profileComNome()));
            when(userSocialLoginGateway.findByProviderAndProviderUserId(anyString(), anyString()))
                    .thenReturn(Optional.of(link));
            when(userGateway.findById(any())).thenReturn(Optional.of(user));
            when(tokenHasher.generate()).thenReturn("raw-refresh");
            when(tokenHasher.hash("raw-refresh")).thenReturn("hashed-refresh");
            when(sessionGateway.create(any())).thenThrow(new RuntimeException("Erro ao criar sessão"));

            final var result = useCase.execute(command("google", "id-token"));

            assertTrue(result.isLeft());
        }
    }
}
