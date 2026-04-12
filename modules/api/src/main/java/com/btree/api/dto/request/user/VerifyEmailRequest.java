package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "token é obrigatório")
        String token
) {}