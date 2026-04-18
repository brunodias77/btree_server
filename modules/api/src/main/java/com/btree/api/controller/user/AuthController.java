package com.btree.api.controller.user;

import com.btree.api.dto.request.user.*;
import com.btree.api.dto.response.user.LoginUserResponse;
import com.btree.api.dto.response.user.RefreshTokenResponse;
import com.btree.api.dto.response.user.RegisterUserResponse;
import com.btree.application.usecase.user.auth.login.LoginUserCommand;
import com.btree.application.usecase.user.auth.login.LoginUserUseCase;
import com.btree.application.usecase.user.auth.logout.LogoutUserCommand;
import com.btree.application.usecase.user.auth.logout.LogoutUserUseCase;
import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionCommand;
import com.btree.application.usecase.user.auth.refresh_session.RefreshSessionUseCase;
import com.btree.application.usecase.user.auth.register.RegisterUserCommand;
import com.btree.application.usecase.user.auth.register.RegisterUserUseCase;
import com.btree.application.usecase.user.auth.reset_password.ResetPasswordCommand;
import com.btree.application.usecase.user.auth.reset_password.ResetPasswordUseCase;
import com.btree.application.usecase.user.auth.verify_email.VerifyEmailCommand;
import com.btree.application.usecase.user.auth.verify_email.VerifyEmailUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Registro, login e gestao de sessao")
public class AuthController {

    private final RegisterUserUseCase _registerUserUseCase;
    private final LoginUserUseCase _loginUserUseCase;
    private final VerifyEmailUseCase _verifyEmailUseCase;
    private final RefreshSessionUseCase _refreshSessionUseCase;
    private final LogoutUserUseCase _logoutUserUseCase;
    private final ResetPasswordUseCase _resetPasswordUseCase;

    public AuthController(RegisterUserUseCase _registerUserUseCase, LoginUserUseCase _loginUserUseCase, VerifyEmailUseCase _verifyEmailUseCase, RefreshSessionUseCase _refreshSessionUseCase, LogoutUserUseCase _logoutUserUseCase, ResetPasswordUseCase _resetPasswordUseCase) {
        this._registerUserUseCase = _registerUserUseCase;
        this._loginUserUseCase = _loginUserUseCase;
        this._verifyEmailUseCase = _verifyEmailUseCase;
        this._refreshSessionUseCase = _refreshSessionUseCase;
        this._logoutUserUseCase = _logoutUserUseCase;
        this._resetPasswordUseCase = _resetPasswordUseCase;
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
    public RegisterUserResponse register(@Valid @RequestBody final RegisterUserRequest request){
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
    public LoginUserResponse login(@Valid @RequestBody final LoginUserRequest request, final HttpServletRequest httpRequest){
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
    public void forgotPassword(@Valid @RequestBody final ResetPasswordRequest request) {
        this._resetPasswordUseCase.execute(new ResetPasswordCommand(request.email()))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

}
