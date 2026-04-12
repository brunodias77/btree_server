package com.btree.api.controller.user;

import com.btree.api.dto.request.user.LoginUserRequest;
import com.btree.api.dto.request.user.RegisterUserRequest;
import com.btree.api.dto.request.user.VerifyEmailRequest;
import com.btree.api.dto.response.user.LoginUserResponse;
import com.btree.api.dto.response.user.RegisterUserResponse;
import com.btree.application.usecase.user.auth.login.LoginUserCommand;
import com.btree.application.usecase.user.auth.login.LoginUserUseCase;
import com.btree.application.usecase.user.auth.register.RegisterUserCommand;
import com.btree.application.usecase.user.auth.register.RegisterUserUseCase;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Registro, login e gestao de sessao")
public class AuthController {

    private final RegisterUserUseCase _registerUserUseCase;
    private final LoginUserUseCase _loginUserUseCase;
    private final VerifyEmailUseCase _verifyEmailUseCase;

    public AuthController(RegisterUserUseCase _registerUserUseCase, LoginUserUseCase _loginUserUseCase, VerifyEmailUseCase _verifyEmailUseCase) {
        this._registerUserUseCase = _registerUserUseCase;
        this._loginUserUseCase = _loginUserUseCase;
        this._verifyEmailUseCase = _verifyEmailUseCase;
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
}
