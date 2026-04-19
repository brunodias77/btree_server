package com.btree.api.dto.request.catalog.brand;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO HTTP de entrada para {@code POST /api/v1/catalog/brands}.
 */
public record CreateBrandRequest(
        @NotBlank(message = "'name' é obrigatório")
        @Size(max = 200, message = "'name' deve ter no máximo 200 caracteres")
        String name,

        @NotBlank(message = "'slug' é obrigatório")
        @Size(max = 256, message = "'slug' deve ter no máximo 256 caracteres")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Formato de slug inválido. Use apenas letras minúsculas, números e hífens"
        )
        String slug,

        String description,

        @JsonProperty("logo_url")
        @Size(max = 512, message = "'logo_url' deve ter no máximo 512 caracteres")
        String logoUrl
) {}

