package com.btree.api.controller.coupon;

import com.btree.api.dto.request.coupon.UpdateCouponRequest;
import com.btree.api.dto.response.coupon.UpdateCouponResponse;
import com.btree.application.usecase.coupon.update.UpdateCouponCommand;
import com.btree.application.usecase.coupon.update.UpdateCouponUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/coupons")
@Tag(name = "Coupons", description = "Gestão de cupons de desconto")
public class CouponController {

    private final UpdateCouponUseCase updateCouponUseCase;

    public CouponController(final UpdateCouponUseCase updateCouponUseCase) {
        this.updateCouponUseCase = updateCouponUseCase;
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Atualizar cupom",
            description = "Edita valores e validade de um cupom existente. " +
                          "Campos imutáveis (code, coupon_type, coupon_scope, status) não são alterados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",  description = "Cupom atualizado com sucesso"),
            @ApiResponse(responseCode = "400",  description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404",  description = "Cupom não encontrado"),
            @ApiResponse(responseCode = "409",  description = "Conflito de versão (edição concorrente)"),
            @ApiResponse(responseCode = "422",  description = "Regras de negócio violadas")
    })
    public UpdateCouponResponse update(
            @PathVariable final UUID id,
            @Valid @RequestBody final UpdateCouponRequest request
    ) {
        final var command = new UpdateCouponCommand(
                id,
                request.description(),
                request.discountValue(),
                request.minOrderValue(),
                request.maxDiscountAmount(),
                request.maxUses(),
                request.maxUsesPerUser(),
                request.startsAt(),
                request.expiresAt(),
                request.eligibleCategoryIds(),
                request.eligibleProductIds(),
                request.eligibleBrandIds(),
                request.eligibleUserIds()
        );
        return UpdateCouponResponse.from(
                updateCouponUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
