package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record LoginSocialRequest(
        @NotBlank(message = "token é obrigatório")
        String token
) {
}
