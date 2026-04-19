package com.btree.api.controller.user;

import com.btree.api.dto.request.user.AddAddressRequest;
import com.btree.api.dto.request.user.UpdateAddressRequest;
import com.btree.api.dto.response.user.AddAddressResponse;
import com.btree.api.dto.response.user.ListAddressResponse;
import com.btree.api.dto.response.user.SetDefaultAddressResponse;
import com.btree.api.dto.response.user.UpdateAddressResponse;
import com.btree.application.usecase.user.address.add_address.AddAddressCommand;
import com.btree.application.usecase.user.address.add_address.AddAddressUseCase;
import com.btree.application.usecase.user.address.delete_address.DeleteAddressCommand;
import com.btree.application.usecase.user.address.delete_address.DeleteAddressUseCase;
import com.btree.application.usecase.user.address.list_address.ListAddressCommand;
import com.btree.application.usecase.user.address.list_address.ListAddressUseCase;
import com.btree.application.usecase.user.address.set_default_address.SetDefaultAddressCommand;
import com.btree.application.usecase.user.address.set_default_address.SetDefaultAddressUseCase;
import com.btree.application.usecase.user.address.update_address.UpdateAddressCommand;
import com.btree.application.usecase.user.address.update_address.UpdateAddressUseCase;
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
@RequestMapping("/v1/users/me/addresses")
@Tag(name = "Addresses", description = "Gerenciamento de endereços do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddAddressUseCase addAddressUseCase;
    private final ListAddressUseCase listAddressUseCase;
    private final UpdateAddressUseCase updateAddressUseCase;
    private final DeleteAddressUseCase deleteAddressUseCase;
    private final SetDefaultAddressUseCase setDefaultAddressUseCase;

    public AddressController(AddAddressUseCase addAddressUseCase, ListAddressUseCase listAddressUseCase, UpdateAddressUseCase updateAddressUseCase, DeleteAddressUseCase deleteAddressUseCase, SetDefaultAddressUseCase setDefaultAddressUseCase) {
        this.addAddressUseCase = addAddressUseCase;
        this.listAddressUseCase = listAddressUseCase;
        this.updateAddressUseCase = updateAddressUseCase;
        this.deleteAddressUseCase = deleteAddressUseCase;
        this.setDefaultAddressUseCase = setDefaultAddressUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar endereço",
            description = "Adiciona um novo endereço de entrega à conta do usuário autenticado. " +
                    "Se for o primeiro endereço, é automaticamente marcado como padrão."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Endereço cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de CEP, UF, etc.)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public AddAddressResponse add(@Valid @RequestBody final AddAddressRequest request) {
        final String userId = currentUserId();

        final var command = new AddAddressCommand(
                userId,
                request.label(),
                request.recipientName(),
                request.street(),
                request.number(),
                request.complement(),
                request.neighborhood(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.country() != null ? request.country() : "BR",
                request.isBillingAddress()
        );

        return AddAddressResponse.from(
                addAddressUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar endereços",
            description = "Retorna todos os endereços de entrega do usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    public ListAddressResponse list() {
        final var command = new ListAddressCommand(currentUserId());

        return ListAddressResponse.from(
                listAddressUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UpdateAddress ──────────────────────────────────────────────

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Editar endereço",
            description = "Atualiza os dados de um endereço existente do usuário autenticado. " +
                    "Enviar payload completo — campos ausentes são gravados como null. " +
                    "O campo isDefault não é alterável por este endpoint (use PATCH /{id}/default)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endereço atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, deletado ou não pertence ao usuário")
    })
    public UpdateAddressResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateAddressRequest request
    ) {
        final String userId = currentUserId();

        return UpdateAddressResponse.from(
                updateAddressUseCase.execute(new UpdateAddressCommand(
                        userId,
                        id,
                        request.label(),
                        request.recipientName(),
                        request.street(),
                        request.number(),
                        request.complement(),
                        request.neighborhood(),
                        request.city(),
                        request.state(),
                        request.postalCode(),
                        request.country() != null ? request.country() : "BR",
                        request.isBillingAddress()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-19: DeleteAddress ──────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remover endereço",
            description = "Aplica soft delete em um endereço do usuário autenticado. " +
                    "O registro é preservado no banco para manter histórico em pedidos anteriores. " +
                    "Não é possível remover o endereço padrão se houver outros endereços ativos — " +
                    "defina outro como padrão antes de remover."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Endereço removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, já removido, " +
                    "não pertence ao usuário ou é o endereço padrão " +
                    "com outros endereços ativos")
    })
    public void delete(@PathVariable final String id) {
        final String userId = currentUserId();

        deleteAddressUseCase
                .execute(new DeleteAddressCommand(userId, id))
                .getOrElseThrow(n -> DomainException.with(n.getErrors()));
    }

    // ── UC-20: SetDefaultAddress ──────────────────────────────────────────

    @PatchMapping("/{id}/default")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Definir endereço padrão",
            description = "Marca um endereço como padrão de entrega, removendo a marcação " +
                    "do endereço padrão anterior em operação atômica. " +
                    "Se o endereço já for o padrão, a operação é idempotente — retorna 200 sem modificações."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endereço definido como padrão com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Endereço não encontrado, deletado ou não pertence ao usuário")
    })
    public SetDefaultAddressResponse setDefault(@PathVariable final String id) {
        final String userId = currentUserId();

        return SetDefaultAddressResponse.from(
                setDefaultAddressUseCase
                        .execute(new SetDefaultAddressCommand(userId, id))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }


    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
