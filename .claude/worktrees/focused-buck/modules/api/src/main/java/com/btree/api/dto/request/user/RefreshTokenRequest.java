package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}
