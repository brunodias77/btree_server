package com.btree.api.dto.request.user;


import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}
