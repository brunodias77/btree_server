package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginUserRequest(
        @NotBlank(message = "identifier é obrigatório")
        @Size(max = 256, message = "identifier deve ter no máximo 256 caracteres")
        String identifier,

        @NotBlank(message = "password é obrigatório")
        String password
) {}
