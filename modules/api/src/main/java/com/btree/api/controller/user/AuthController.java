package com.btree.api.controller.user;

import com.btree.api.dto.request.user.*;
import com.btree.api.dto.response.user.*;
import com.btree.application.usecase.user.auth.confirm_password_reset.ConfirmPasswordResetCommand;
import com.btree.application.usecase.user.auth.confirm_password_reset.ConfirmPasswordResetUseCase;
import com.btree.application.usecase.user.auth.enable_two_factor.EnableTwoFactorCommand;
import com.btree.application.usecase.user.auth.enable_two_factor.EnableTwoFactorUseCase;
import com.btree.application.usecase.user.auth.forgot_password.ForgotPasswordCommand;
import com.btree.application.usecase.user.auth.forgot_password.ForgotPasswordUseCase;
import com.btree.application.usecase.user.auth.login.LoginUserCommand;
import com.btree.application.usecase.user.auth.login.LoginUserUseCase;
import com.btree.application.usecase.user.auth.login_social_provider.LoginSocialProviderCommand;
import com.btree.application.usecase.user.auth.login_social_provider.LoginSocialProviderUseCase;
import com.btree.application.usecase.user.auth.logout.LogoutUserCommand;
import com.btree.application.usecase.user.auth.logout.LogoutUserUseCase;
import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionCommand;
import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionUseCase;
import com.btree.application.usecase.user.auth.register.RegisterUserCommand;
import com.btree.application.usecase.user.auth.register.RegisterUserUseCase;
import com.btree.application.usecase.user.auth.setup_two_factor.SetupTwoFactorCommand;
import com.btree.application.usecase.user.auth.setup_two_factor.SetupTwoFactorUseCase;
import com.btree.application.usecase.user.auth.verify_email.VerifyEmailCommand;
import com.btree.application.usecase.user.auth.verify_email.VerifyEmailUseCase;
import com.btree.application.usecase.user.auth.verify_two_factor.VerifyTwoFactorCommand;
import com.btree.application.usecase.user.auth.verify_two_factor.VerifyTwoFactorUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Registro, login e gestao de sessao")
public class AuthController {

    private final RegisterUserUseCase _registerUserUseCase;
    private final LoginUserUseCase _loginUserUseCase;
    private final VerifyEmailUseCase _verifyEmailUseCase;
    private final RefreshSessionUseCase _refreshSessionUseCase;
    private final LogoutUserUseCase _logoutUserUseCase;
    private final ForgotPasswordUseCase _forgotPasswordUseCase;
    private final ConfirmPasswordResetUseCase _confirmPasswordResetUseCase;
    private final LoginSocialProviderUseCase _loginSocialProviderUseCase;
    private final VerifyTwoFactorUseCase _verifyTwoFactorUseCase;
    private final SetupTwoFactorUseCase _setupTwoFactorUseCase;
    private final EnableTwoFactorUseCase _enableTwoFactorUseCase;

    public AuthController(RegisterUserUseCase _registerUserUseCase, LoginUserUseCase _loginUserUseCase, VerifyEmailUseCase _verifyEmailUseCase, RefreshSessionUseCase _refreshSessionUseCase, LogoutUserUseCase _logoutUserUseCase, ForgotPasswordUseCase _forgotPasswordUseCase, ConfirmPasswordResetUseCase _confirmPasswordResetUseCase, LoginSocialProviderUseCase _loginSocialProviderUseCase, VerifyTwoFactorUseCase _verifyTwoFactorUseCase, SetupTwoFactorUseCase _setupTwoFactorUseCase, EnableTwoFactorUseCase _enableTwoFactorUseCase) {
        this._registerUserUseCase = _registerUserUseCase;
        this._loginUserUseCase = _loginUserUseCase;
        this._verifyEmailUseCase = _verifyEmailUseCase;
        this._refreshSessionUseCase = _refreshSessionUseCase;
        this._logoutUserUseCase = _logoutUserUseCase;
        this._forgotPasswordUseCase = _forgotPasswordUseCase;
        this._confirmPasswordResetUseCase = _confirmPasswordResetUseCase;
        this._loginSocialProviderUseCase = _loginSocialProviderUseCase;
        this._verifyTwoFactorUseCase = _verifyTwoFactorUseCase;
        this._setupTwoFactorUseCase = _setupTwoFactorUseCase;
        this._enableTwoFactorUseCase = _enableTwoFactorUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar um novo usuario", description = "cria conta com username, email e senha")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "usuario criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Username ou e-mail já existe"),
            @ApiResponse(responseCode = "422", description = "Regras de negócio violadas")
    })
    public RegisterUserResponse register(@Valid @RequestBody final RegisterUserRequest request) {
        final var command = new RegisterUserCommand(
                request.username(),
                request.email(),
                request.password()
        );
        return RegisterUserResponse.from(_registerUserUseCase.execute(command)
                .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Autenticar usuário", description = "Autentica com username ou e-mail e retorna access/refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou conta impedida")
    })
    public LoginUserResponse login(@Valid @RequestBody final LoginUserRequest request, final HttpServletRequest httpRequest) {
        final var command = new LoginUserCommand(
                request.identifier(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return LoginUserResponse.from(
                _loginUserUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verificar e-mail", description = "Confirma o e-mail do usuário com o token recebido")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail verificado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "422", description = "Token inválido, expirado ou já utilizado")
    })
    public void verifyEmail(@Valid @RequestBody final VerifyEmailRequest request) {
        _verifyEmailUseCase.execute(new VerifyEmailCommand(request.token()))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Renovar sessão", description = "Troca o refresh token por um novo par de tokens (token rotation)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "422", description = "Sessão inválida, revogada ou expirada")
    })
    public RefreshTokenResponse refresh(
            @Valid @RequestBody final RefreshTokenRequest request,
            final HttpServletRequest httpRequest
    ) {
        final var command = new RefreshSessionCommand(
                request.refreshToken(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return RefreshTokenResponse.from(
                _refreshSessionUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerrar sessão", description = "Revoga o refresh token, impedindo renovação futura de tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Refresh token ausente ou inválido")
    })
    public void logout(@Valid @RequestBody final LogoutRequest request) {
        _logoutUserUseCase.execute(new LogoutUserCommand(request.refreshToken()))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Solicitar redefinição de senha",
            description = "Gera um token temporário e envia e-mail com link de redefinição. "
                    + "Sempre retorna 200 independentemente da existência do e-mail (anti-enumeration)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação processada"),
            @ApiResponse(responseCode = "422", description = "E-mail ausente ou com formato inválido")
    })
    public void forgotPassword(@Valid @RequestBody final ForgotPasswordRequest request) {
        this._forgotPasswordUseCase.execute(new ForgotPasswordCommand(request.email()))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Redefinir senha",
            description = "Define a nova senha usando o token de redefinição recebido por e-mail. "
                    + "O token é invalidado após o uso (uso único)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "422", description = "Token inválido, expirado ou já utilizado; ou senha fraca")
    })
    public void resetPassword(@Valid @RequestBody final ConfirmPasswordResetRequest request) {
        this._confirmPasswordResetUseCase.execute(
                new ConfirmPasswordResetCommand(request.token(), request.newPassword())
        ).getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    @PostMapping("/social/{provider}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Login com provedor social",
            description = "Autentica ou registra usuário via OAuth2/OIDC (ex: Google). "
                    + "Cria conta automaticamente se o e-mail não existir."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login social realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Provedor não suportado"),
            @ApiResponse(responseCode = "401", description = "Token social inválido ou expirado"),
            @ApiResponse(responseCode = "403", description = "Conta desativada")
    })
    public LoginSocialResponse socialLogin(
            @PathVariable final String provider,
            @Valid @RequestBody final LoginSocialRequest request,
            final HttpServletRequest httpRequest
    ) {
        final var input = new LoginSocialProviderCommand(
                provider,
                request.token(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return LoginSocialResponse.from(
                _loginSocialProviderUseCase.execute(input)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @PostMapping("/2fa/verify")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Verificar código 2FA",
            description = "Segunda etapa do login com 2FA. Valida o código TOTP e retorna os tokens de acesso finais."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificação realizada com sucesso — tokens emitidos"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Código inválido, transação expirada ou já utilizada"),
            @ApiResponse(responseCode = "403", description = "Conta desativada")
    })
    public VerifyTwoFactorResponse verifyTwoFactor(
            @Valid @RequestBody final VerifyTwoFactorRequest request,
            final HttpServletRequest httpRequest
    ) {
        final var input = new VerifyTwoFactorCommand(
                request.transactionId(),
                request.code(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return VerifyTwoFactorResponse.from(
                _verifyTwoFactorUseCase.execute(input)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    /**
     * Inicia o processo de ativação do 2FA.
     *
     * <p>Gera um novo secret TOTP, persiste temporariamente (15 min) como
     * {@code UserToken} do tipo {@code TWO_FACTOR_SETUP} e retorna:
     * <ul>
     *   <li>O {@code setup_token_id} — necessário para confirmar via {@code /enable}</li>
     *   <li>O {@code secret} em Base32 — para configuração manual no app autenticador</li>
     *   <li>O {@code qr_code_uri} — URI {@code otpauth://} para geração do QR Code</li>
     * </ul>
     *
     * <p>Se o usuário já tiver 2FA ativo, retorna {@code 409 Conflict}.
     */
    @PostMapping("/2fa/setup")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Iniciar configuração de 2FA",
            description = "Gera o secret TOTP e retorna a URI do QR Code para configuração no app autenticador."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Setup iniciado — exibir QR Code ao usuário"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "409", description = "2FA já está ativado para este usuário")
    })
    public SetupTwoFactorResponse setup() {
        final String userId = currentUserId();
        return SetupTwoFactorResponse.from(
                this._setupTwoFactorUseCase.execute(new SetupTwoFactorCommand(userId))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }



    /**
     * Confirma a ativação do 2FA com o código TOTP gerado pelo app.
     *
     * <p>Valida o código contra o secret armazenado no token de setup e,
     * em caso de sucesso, marca {@code twoFactorEnabled = true} no {@code User}.
     * O token de setup é invalidado após o uso (idempotência).
     */
    @PostMapping("/2fa/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Confirmar ativação de 2FA",
            description = "Valida o código TOTP e ativa o 2FA na conta. O token de setup expira em 15 minutos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "2FA ativado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código TOTP inválido ou campos ausentes"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Token de setup expirado, já utilizado ou inválido")
    })
    public void enable(@Valid @RequestBody final EnableTwoFactorRequest request) {
        final String userId = currentUserId();
        this._enableTwoFactorUseCase.execute(new EnableTwoFactorCommand(userId, request.setupTokenId(), request.code()))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
