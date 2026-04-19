package com.btree.api.controller.user;

import com.btree.api.dto.response.user.CurrentUserResponse;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserInput;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserOutput;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users", description = "Dados e gerenciamento de usuario autenticado")
public class UserController {

    private final GetCurrentUserUseCase _getCurrentUserUseCase;

    public UserController(GetCurrentUserUseCase getCurrentUserUseCase) {
        this._getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Obter usuario atual",
            description = "Retorna os dados consolidados do usuario autenticado (perfil, role e etc..)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do usuário retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public CurrentUserResponse me(){
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final String userId = auth.getName();

        final GetCurrentUserOutput output = _getCurrentUserUseCase
                .execute(new GetCurrentUserInput(userId))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));

        return CurrentUserResponse.from(output);
    }
}
