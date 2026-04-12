package com.btree.api.controller.user;

import com.btree.api.dto.request.user.RegisterUserRequest;
import com.btree.api.dto.response.user.RegisterUserResponse;
import com.btree.application.usecase.user.auth.register.RegisterUserCommand;
import com.btree.application.usecase.user.auth.register.RegisterUserUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public AuthController(RegisterUserUseCase _registerUserUseCase) {
        this._registerUserUseCase = _registerUserUseCase;
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
}
