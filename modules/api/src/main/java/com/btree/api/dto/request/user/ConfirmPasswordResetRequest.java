package com.btree.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(
        @NotBlank(message = "token é obrigatório")
        String token,

        @NotBlank(message = "newPassword é obrigatório")
        @Size(min = 8, max = 256, message = "newPassword deve ter entre 8 e 256 caracteres")
        String newPassword
) {}
