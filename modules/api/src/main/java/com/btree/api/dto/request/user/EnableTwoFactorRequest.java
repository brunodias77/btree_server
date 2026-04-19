package com.btree.api.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnableTwoFactorRequest(
        @NotBlank(message = "setup_token_id é obrigatório")
        @JsonProperty("setup_token_id") String setupTokenId,

        @NotBlank(message = "code é obrigatório")
        @Size(min = 6, max = 6, message = "code deve ter exatamente 6 dígitos")
        @Pattern(regexp = "\\d{6}", message = "code deve conter apenas dígitos")
        String code
) {
}
