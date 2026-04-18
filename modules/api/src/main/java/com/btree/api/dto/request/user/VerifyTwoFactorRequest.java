package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyTwoFactorRequest(
        @NotBlank(message = "transaction_id é obrigatório")
        String transactionId,

        @NotBlank(message = "code é obrigatório")
        @Pattern(regexp = "\\d{6}", message = "code deve conter exatamente 6 dígitos numéricos")
        String code
) {
}
