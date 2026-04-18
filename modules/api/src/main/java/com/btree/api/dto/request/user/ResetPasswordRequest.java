package com.btree.api.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        String email
) { }
