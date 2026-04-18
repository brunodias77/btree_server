package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record ValidateResetTokenRequest(
        @NotBlank(message = "token é obrigatório")
        String token
) {}
