package com.btree.api.controller.cart;

import com.btree.api.dto.response.cart.GetCartByIdResponse;
import com.btree.application.usecase.cart.get_by_id.GetCartByIdCommand;
import com.btree.application.usecase.cart.get_by_id.GetCartByIdUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cart")
@Tag(name = "Cart", description = "Gerenciamento do carrinho de compras")
public class CartController {

    private final GetCartByIdUseCase getCartByIdUseCase;

    public CartController(GetCartByIdUseCase getCartByIdUseCase) {
        this.getCartByIdUseCase = getCartByIdUseCase;
    }

    // ── UC-90 GetCart ─────────────────────────────────────────

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Obter carrinho ativo",
            description = "Retorna o carrinho ativo do usuário com preços atuais de catálogo. " +
                    "Sinaliza itens com preço alterado desde a adição via 'priceChanged = true'. " +
                    "Carrinhos guest devem passar o sessionId como query param.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrinho retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Nenhum carrinho ativo encontrado"),
            @ApiResponse(responseCode = "422", description = "Identificação inválida ou ausente")
    })
    @SecurityRequirement(name = "bearerAuth")
    public GetCartByIdResponse getCart(
            @RequestHeader(value = "X-User-Id", required = false) final String userId,
            @RequestParam(value = "sessionId", required = false) final String sessionId
    ) {
        final var command = new GetCartByIdCommand(userId, sessionId);
        return GetCartByIdResponse.from(
                getCartByIdUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
