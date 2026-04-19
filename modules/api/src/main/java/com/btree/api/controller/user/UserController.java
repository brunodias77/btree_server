package com.btree.api.controller.user;

import com.btree.api.dto.request.user.UpdateProfileRequest;
import com.btree.api.dto.response.user.CurrentUserResponse;
import com.btree.api.dto.response.user.GetProfileResponse;
import com.btree.api.dto.response.user.UpdateProfileResponse;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserInput;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserOutput;
import com.btree.application.usecase.user.get_current_user.GetCurrentUserUseCase;
import com.btree.application.usecase.user.get_profile.GetProfileCommand;
import com.btree.application.usecase.user.get_profile.GetProfileUseCase;
import com.btree.application.usecase.user.update_profile.UpdateProfileCommand;
import com.btree.application.usecase.user.update_profile.UpdateProfileUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users", description = "Dados e gerenciamento de usuario autenticado")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final GetProfileUseCase getProfileUseCase;

    public UserController(
            final GetCurrentUserUseCase getCurrentUserUseCase,
            final UpdateProfileUseCase updateProfileUseCase,
            final GetProfileUseCase getProfileUseCase
    ) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.getProfileUseCase = getProfileUseCase;
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
    public CurrentUserResponse me() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final String userId = auth.getName();

        final GetCurrentUserOutput output = getCurrentUserUseCase
                .execute(new GetCurrentUserInput(userId))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));

        return CurrentUserResponse.from(output);
    }

    @PutMapping("/me/profile")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Atualizar perfil",
            description = "Edita os dados pessoais do usuário autenticado: nome, CPF, " +
                    "data de nascimento, idioma, moeda e preferência de newsletter. " +
                    "Todos os campos são opcionais — envie o payload completo com os " +
                    "valores desejados (campos ausentes são gravados como null)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Formato de campo inválido (Bean Validation)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking) ou CPF já em uso"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public UpdateProfileResponse update(@Valid @RequestBody final UpdateProfileRequest request) {
        final var command = new UpdateProfileCommand(
                currentUserId(),
                request.firstName(),
                request.lastName(),
                request.cpf(),
                request.birthDate(),
                request.gender(),
                request.preferredLanguage(),
                request.preferredCurrency(),
                request.newsletterSubscribed()
        );

        return UpdateProfileResponse.from(
                updateProfileUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Obter perfil",
            description = "Retorna o perfil completo do usuário autenticado, incluindo dados pessoais, " +
                    "preferências e metadados de aceite de termos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public GetProfileResponse get() {
        return getProfileUseCase.execute(new GetProfileCommand(currentUserId()))
                .map(GetProfileResponse::from)
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
