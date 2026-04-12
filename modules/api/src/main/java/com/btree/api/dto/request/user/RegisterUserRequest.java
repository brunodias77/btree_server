package com.btree.api.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @NotBlank(message = "username é obrigatório")
        @Size(max = 256, message = "username deve ter no máximo 256 caracteres")
        String username,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ter formato válido")
        @Size(max = 256, message = "email deve ter no máximo 256 caracteres")
        String email,

        @NotBlank(message = "password é obrigatório")
        @Size(min = 8, max = 256, message = "password deve ter entre 8 e 256 caracteres")
        String password
) {
}
